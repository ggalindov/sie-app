package sie.siejuridicos.common.cifrado;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;

// Cifrado de campos sensibles a nivel de aplicación (AES-256-GCM), independiente de si el
// disco del servidor de base de datos está o no cifrado: protege el dato aunque alguien
// obtenga acceso directo a la base (un volcado, una copia de seguridad robada, una
// consulta SQL directa que se salte la aplicación).
//
// Dos operaciones con propósitos distintos, a propósito no intercambiables:
// - cifrar()/descifrar(): IV aleatorio en cada llamada (no determinista). Dos cifrados del
//   mismo valor dan resultados distintos, así que NO sirve para buscar por igualdad en SQL.
//   Se usa para datos que solo se necesita mostrar (nombre, teléfono, notas internas).
// - indiceCiego(): HMAC-SHA256 determinista, no reversible. El mismo valor de entrada
//   siempre da la misma salida, así que SÍ sirve para buscar/comparar por igualdad (ej.
//   encontrar un cliente por correo) sin guardar el correo en texto plano en un índice.
//
// La llave de cifrado y la de HMAC se derivan de una sola llave maestra (app.cifrado.clave)
// con HMAC-SHA256 sobre una etiqueta fija distinta para cada uso, en vez de reusar los
// mismos bytes crudos para dos algoritmos distintos (mala práctica criptográfica: mezclar
// el uso de una llave entre AES y HMAC facilita ciertos ataques de reutilización de llave).
@Service
public class CifradoService {

    private static final String ALGORITMO_AES = "AES/GCM/NoPadding";
    private static final int TAMANO_IV_BYTES = 12;
    private static final int TAMANO_TAG_BITS = 128;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final SecretKeySpec claveCifrado;
    private final SecretKeySpec claveHmac;

    public CifradoService(@Value("${app.cifrado.clave}") String claveMaestraBase64) {
        byte[] claveMaestra;
        try {
            claveMaestra = Base64.getDecoder().decode(claveMaestraBase64);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "app.cifrado.clave (DATA_ENCRYPTION_KEY) no es Base64 válido", ex);
        }
        if (claveMaestra.length != 32) {
            throw new IllegalStateException(
                    "app.cifrado.clave (DATA_ENCRYPTION_KEY) debe decodificar a exactamente 32 "
                            + "bytes (AES-256). Genera una nueva con: openssl rand -base64 32");
        }
        this.claveCifrado = new SecretKeySpec(derivar(claveMaestra, "sie-cifrado-aes"), "AES");
        this.claveHmac = new SecretKeySpec(derivar(claveMaestra, "sie-cifrado-hmac"), "HmacSHA256");
    }

    private static byte[] derivar(byte[] claveMaestra, String etiqueta) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(claveMaestra, "HmacSHA256"));
            return mac.doFinal(etiqueta.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("No se pudo derivar la llave de cifrado", ex);
        }
    }

    public String cifrar(String textoPlano) {
        if (textoPlano == null) {
            return null;
        }
        try {
            byte[] iv = new byte[TAMANO_IV_BYTES];
            ALEATORIO.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITMO_AES);
            cipher.init(Cipher.ENCRYPT_MODE, claveCifrado, new GCMParameterSpec(TAMANO_TAG_BITS, iv));
            byte[] cifrado = cipher.doFinal(textoPlano.getBytes(StandardCharsets.UTF_8));

            byte[] combinado = new byte[iv.length + cifrado.length];
            System.arraycopy(iv, 0, combinado, 0, iv.length);
            System.arraycopy(cifrado, 0, combinado, iv.length, cifrado.length);
            return Base64.getEncoder().encodeToString(combinado);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("No se pudo cifrar el valor", ex);
        }
    }

    public String descifrar(String valorCifrado) {
        if (valorCifrado == null) {
            return null;
        }
        try {
            byte[] combinado = Base64.getDecoder().decode(valorCifrado);
            byte[] iv = Arrays.copyOfRange(combinado, 0, TAMANO_IV_BYTES);
            byte[] cifrado = Arrays.copyOfRange(combinado, TAMANO_IV_BYTES, combinado.length);

            Cipher cipher = Cipher.getInstance(ALGORITMO_AES);
            cipher.init(Cipher.DECRYPT_MODE, claveCifrado, new GCMParameterSpec(TAMANO_TAG_BITS, iv));
            byte[] plano = cipher.doFinal(cifrado);
            return new String(plano, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            // IllegalArgumentException: Base64 inválido. GeneralSecurityException (incluye
            // AEADBadTagException): el tag de autenticación no coincide -- o cambió la llave,
            // o el valor fue alterado. En ambos casos, mismo mensaje genérico: no hay forma
            // de distinguirlos sin filtrar información útil para un atacante.
            throw new IllegalStateException(
                    "No se pudo descifrar el valor (¿cambió la llave de cifrado?)", ex);
        }
    }

    // Determinista y no reversible: mismo valor de entrada -> misma salida siempre, para
    // poder comparar por igualdad en SQL sin guardar el valor en texto plano. normalizar()
    // asegura que "Correo@Ejemplo.com" y " correo@ejemplo.com " produzcan el mismo índice.
    public String indiceCiego(String valor) {
        if (valor == null) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(claveHmac);
            byte[] resultado = mac.doFinal(normalizar(valor).getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(resultado);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("No se pudo calcular el índice de búsqueda", ex);
        }
    }

    private static String normalizar(String valor) {
        return valor.strip().toLowerCase(Locale.ROOT);
    }
}
