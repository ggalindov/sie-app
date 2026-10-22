package sie.siejuridicos.documento;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

// Cadena de hashes de eventos (ver HashChainCalculator): esto es lo que hace detectable
// cualquier alteración o borrado de un evento ya registrado (DocumentoEventoChainVerifier
// recalcula y compara). Estas pruebas verifican la propiedad central: el eslabón depende de
// TODOS los campos del evento más el hash anterior, así que cambiar cualquiera de ellos --
// incluido el propio encadenamiento -- cambia el resultado.
class HashChainCalculatorTest {

    private static final String CLAVE_MAESTRA = Base64.getEncoder().encodeToString(new byte[32]);
    private static final UUID DOC_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final LocalDateTime OCURRIDO_EN = LocalDateTime.of(2026, 1, 1, 12, 0, 0);

    private HashChainCalculator calculadora() {
        return new HashChainCalculator(new DocumentoClaveMaestraService(CLAVE_MAESTRA));
    }

    private String eslabonBase(HashChainCalculator c) {
        return c.calcular(DOC_ID, 1L, HashChainCalculator.GENESIS, "DILIGENCIADO", "STAFF", 1L, null,
                "descripción", "127.0.0.1", "agente", "hash-doc", OCURRIDO_EN);
    }

    @Test
    void mismosDatosProducenElMismoEslabon() {
        HashChainCalculator c = calculadora();
        assertEquals(eslabonBase(c), eslabonBase(c));
    }

    @Test
    void cambiarLaDescripcionCambiaElEslabon() {
        HashChainCalculator c = calculadora();
        String original = eslabonBase(c);
        String alterado = c.calcular(DOC_ID, 1L, HashChainCalculator.GENESIS, "DILIGENCIADO", "STAFF", 1L, null,
                "descripción MODIFICADA", "127.0.0.1", "agente", "hash-doc", OCURRIDO_EN);
        assertNotEquals(original, alterado);
    }

    @Test
    void cambiarElHashDelEventoAnteriorCambiaElEslabon() {
        // Esta es la propiedad que hace que borrar o alterar un evento intermedio rompa
        // la cadena a partir de ese punto, no solo en ese evento.
        HashChainCalculator c = calculadora();
        String conGenesis = eslabonBase(c);
        String conOtroAnterior = c.calcular(DOC_ID, 1L, "f".repeat(64), "DILIGENCIADO", "STAFF", 1L, null,
                "descripción", "127.0.0.1", "agente", "hash-doc", OCURRIDO_EN);
        assertNotEquals(conGenesis, conOtroAnterior);
    }

    @Test
    void cambiarLaSecuenciaCambiaElEslabon() {
        HashChainCalculator c = calculadora();
        String secuencia1 = eslabonBase(c);
        String secuencia2 = c.calcular(DOC_ID, 2L, HashChainCalculator.GENESIS, "DILIGENCIADO", "STAFF", 1L, null,
                "descripción", "127.0.0.1", "agente", "hash-doc", OCURRIDO_EN);
        assertNotEquals(secuencia1, secuencia2);
    }

    @Test
    void camposNulosNoRompenElCalculoYSiguenAfectandoElResultado() {
        HashChainCalculator c = calculadora();
        String conIp = eslabonBase(c);
        String sinIp = c.calcular(DOC_ID, 1L, HashChainCalculator.GENESIS, "DILIGENCIADO", "STAFF", 1L, null,
                "descripción", null, "agente", "hash-doc", OCURRIDO_EN);
        assertNotEquals(conIp, sinIp);
    }

    @Test
    void documentosDistintosProducenEslabonesDistintosConElMismoRestoDeDatos() {
        HashChainCalculator c = calculadora();
        String deEsteDocumento = eslabonBase(c);
        String deOtroDocumento = c.calcular(UUID.fromString("22222222-2222-2222-2222-222222222222"), 1L,
                HashChainCalculator.GENESIS, "DILIGENCIADO", "STAFF", 1L, null, "descripción", "127.0.0.1",
                "agente", "hash-doc", OCURRIDO_EN);
        assertNotEquals(deEsteDocumento, deOtroDocumento);
    }

    @Test
    void elEslabonEsUnHexadecimalDeSesentaYCuatroCaracteres() {
        String eslabon = eslabonBase(calculadora());
        assertEquals(64, eslabon.length());
        assertEquals(eslabon, eslabon.toLowerCase());
    }
}
