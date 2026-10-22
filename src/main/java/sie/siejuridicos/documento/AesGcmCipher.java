package sie.siejuridicos.documento;

import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

// Primitiva AES-256-GCM de bajo nivel, parametrizada por una clave explícita en cada
// llamado -- reusada tanto para cifrar el contenido de un documento con su DEK como para
// envolver esa DEK (o una llave privada de firma de aplicación/PAdES) con la KEK activa.
@Component
public class AesGcmCipher {

    private static final String TRANSFORMACION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int IV_LENGTH_BYTES = 12;
    private static final int CLAVE_LENGTH_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();

    public record Resultado(byte[] datosCifrados, byte[] iv) {
    }

    public Resultado cifrar(byte[] datos, byte[] claveBytes) {
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMACION);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(claveBytes, "AES"), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new Resultado(cipher.doFinal(datos), iv);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("No se pudo cifrar.", ex);
        }
    }

    public byte[] descifrar(byte[] datosCifrados, byte[] iv, byte[] claveBytes) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMACION);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(claveBytes, "AES"), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return cipher.doFinal(datosCifrados);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("No se pudo descifrar.", ex);
        }
    }

    // Clave de datos (DEK) aleatoria de 256 bits, única por documento.
    public byte[] generarClaveAleatoria() {
        byte[] clave = new byte[CLAVE_LENGTH_BYTES];
        secureRandom.nextBytes(clave);
        return clave;
    }
}
