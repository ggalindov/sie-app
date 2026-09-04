package sie.siejuridicos.common.cifrado;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Componente puro en memoria, sin contexto de Spring: se puede probar exactamente lo que un
// atacante con acceso a la base de datos (pero no a la llave) vería.
class CifradoServiceTest {

    private static final String CLAVE_VALIDA = Base64.getEncoder().encodeToString(new byte[32]);

    private CifradoService servicio() {
        return new CifradoService(CLAVE_VALIDA);
    }

    @Test
    void cifrarYDescifrarDevuelveElValorOriginal() {
        CifradoService servicio = servicio();
        String original = "cliente.real@example.com";
        String cifrado = servicio.cifrar(original);
        assertEquals(original, servicio.descifrar(cifrado));
    }

    @Test
    void elValorCifradoNuncaEsIgualAlTextoPlano() {
        // La razón misma de existir de este servicio: si esto fallara, estaríamos
        // "cifrando" en texto plano.
        CifradoService servicio = servicio();
        String original = "3001234567";
        assertNotEquals(original, servicio.cifrar(original));
    }

    @Test
    void dosCigradosDelMismoValorDanResultadosDistintos() {
        // IV aleatorio en cada llamada: dos filas con el mismo correo NO deben verse
        // idénticas en la base de datos con solo mirar la columna cifrada (evita que un
        // atacante con acceso de solo lectura a la base infiera "estos dos clientes tienen
        // el mismo correo" sin poder descifrar nada).
        CifradoService servicio = servicio();
        String original = "mismo@example.com";
        assertNotEquals(servicio.cifrar(original), servicio.cifrar(original));
    }

    @Test
    void unValorCifradoAlteradoNoDescifra() {
        // Simula un atacante modificando bytes en la base de datos directamente (sin pasar
        // por la aplicación): GCM debe detectarlo y rechazar, nunca devolver texto corrupto
        // en silencio.
        CifradoService servicio = servicio();
        String cifrado = servicio.cifrar("dato-sensible");
        byte[] bytes = Base64.getDecoder().decode(cifrado);
        bytes[bytes.length - 1] ^= 0x01; // voltea el último bit del tag de autenticación
        String alterado = Base64.getEncoder().encodeToString(bytes);
        assertThrows(IllegalStateException.class, () -> servicio.descifrar(alterado));
    }

    @Test
    void indiceCiegoEsDeterministaParaElMismoValor() {
        CifradoService servicio = servicio();
        String correo = "cliente@example.com";
        assertEquals(servicio.indiceCiego(correo), servicio.indiceCiego(correo));
    }

    @Test
    void indiceCiegoIgnoraMayusculasYEspacios() {
        CifradoService servicio = servicio();
        assertEquals(
                servicio.indiceCiego("Cliente@Example.com"),
                servicio.indiceCiego("  cliente@example.com  "));
    }

    @Test
    void indiceCiegoDeValoresDistintosEsDistinto() {
        CifradoService servicio = servicio();
        assertNotEquals(servicio.indiceCiego("a@example.com"), servicio.indiceCiego("b@example.com"));
    }

    @Test
    void indiceCiegoNoEsReversibleAlTextoPlano() {
        // No es un requisito comprobable "por definición" con un assert directo, pero sí
        // podemos confirmar que no es simplemente el cifrado ni el texto plano disfrazado.
        CifradoService servicio = servicio();
        String correo = "cliente@example.com";
        String indice = servicio.indiceCiego(correo);
        assertNotEquals(correo, indice);
        assertNotEquals(servicio.cifrar(correo), indice);
    }

    @Test
    void cifrarNuloDevuelveNulo() {
        assertNull(servicio().cifrar(null));
        assertNull(servicio().descifrar(null));
        assertNull(servicio().indiceCiego(null));
    }

    @Test
    void rechazaUnaLlaveQueNoDecodificaATreintaYDosBytes() {
        String claveCorta = Base64.getEncoder().encodeToString(new byte[16]);
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> new CifradoService(claveCorta));
        assertTrue(ex.getMessage().contains("32 bytes"));
    }

    @Test
    void rechazaUnaLlaveQueNoEsBase64Valido() {
        assertThrows(IllegalStateException.class, () -> new CifradoService("esto no es base64 válido!!"));
    }
}
