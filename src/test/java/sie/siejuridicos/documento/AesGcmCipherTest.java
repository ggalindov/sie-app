package sie.siejuridicos.documento;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Primitiva de cifrado envolvente del motor de firma (ver AesGcmCipher): cifra tanto el
// contenido del documento como las llaves de firma de aplicación/PAdES en reposo. Estas
// pruebas confirman el round-trip y, más importante, que GCM detecta y rechaza cualquier
// alteración del texto cifrado o del IV en vez de devolver datos corruptos en silencio.
class AesGcmCipherTest {

    private static final SecureRandom RANDOM = new SecureRandom();

    private byte[] claveAleatoria() {
        byte[] clave = new byte[32];
        RANDOM.nextBytes(clave);
        return clave;
    }

    @Test
    void cifrarYDescifrarDevuelveLosDatosOriginales() {
        AesGcmCipher cipher = new AesGcmCipher();
        byte[] clave = claveAleatoria();
        byte[] original = "contenido del documento legal".getBytes(StandardCharsets.UTF_8);

        AesGcmCipher.Resultado resultado = cipher.cifrar(original, clave);
        byte[] descifrado = cipher.descifrar(resultado.datosCifrados(), resultado.iv(), clave);

        assertArrayEquals(original, descifrado);
    }

    @Test
    void elTextoCifradoNuncaEsIgualAlOriginal() {
        AesGcmCipher cipher = new AesGcmCipher();
        byte[] original = "dato sensible".getBytes(StandardCharsets.UTF_8);
        AesGcmCipher.Resultado resultado = cipher.cifrar(original, claveAleatoria());
        assertNotEquals(Arrays.toString(original), Arrays.toString(resultado.datosCifrados()));
    }

    @Test
    void dosCifradosDelMismoValorUsanIvDistintoYDanResultadosDistintos() {
        AesGcmCipher cipher = new AesGcmCipher();
        byte[] clave = claveAleatoria();
        byte[] original = "mismo contenido".getBytes(StandardCharsets.UTF_8);

        AesGcmCipher.Resultado a = cipher.cifrar(original, clave);
        AesGcmCipher.Resultado b = cipher.cifrar(original, clave);

        assertNotEquals(Arrays.toString(a.iv()), Arrays.toString(b.iv()));
        assertNotEquals(Arrays.toString(a.datosCifrados()), Arrays.toString(b.datosCifrados()));
    }

    @Test
    void alterarUnByteDelTextoCifradoImpideDescifrar() {
        AesGcmCipher cipher = new AesGcmCipher();
        byte[] clave = claveAleatoria();
        AesGcmCipher.Resultado resultado = cipher.cifrar("contenido".getBytes(StandardCharsets.UTF_8), clave);
        byte[] alterado = resultado.datosCifrados().clone();
        alterado[0] ^= 0x01;
        assertThrows(IllegalStateException.class, () -> cipher.descifrar(alterado, resultado.iv(), clave));
    }

    @Test
    void alterarElIvImpideDescifrar() {
        AesGcmCipher cipher = new AesGcmCipher();
        byte[] clave = claveAleatoria();
        AesGcmCipher.Resultado resultado = cipher.cifrar("contenido".getBytes(StandardCharsets.UTF_8), clave);
        byte[] ivAlterado = resultado.iv().clone();
        ivAlterado[0] ^= 0x01;
        assertThrows(IllegalStateException.class, () -> cipher.descifrar(resultado.datosCifrados(), ivAlterado, clave));
    }

    @Test
    void descifrarConLaClaveEquivocadaFalla() {
        AesGcmCipher cipher = new AesGcmCipher();
        AesGcmCipher.Resultado resultado = cipher.cifrar("contenido".getBytes(StandardCharsets.UTF_8), claveAleatoria());
        assertThrows(IllegalStateException.class, () -> cipher.descifrar(resultado.datosCifrados(), resultado.iv(), claveAleatoria()));
    }

    @Test
    void generarClaveAleatoriaDaTreintaYDosBytesDistintosEnCadaLlamada() {
        AesGcmCipher cipher = new AesGcmCipher();
        byte[] a = cipher.generarClaveAleatoria();
        byte[] b = cipher.generarClaveAleatoria();
        assertEquals(32, a.length);
        assertNotEquals(Arrays.toString(a), Arrays.toString(b));
    }
}
