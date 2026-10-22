package sie.siejuridicos.documento;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class HashUtil {

    private HashUtil() {
    }

    public static String sha256Hex(byte[] datos) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(datos);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible en esta JVM.", ex);
        }
    }

    // Inverso de sha256Hex: usado por Rfc3161TimestampClient, que necesita el hash como bytes.
    public static byte[] hexToBytes(String hex) {
        int longitud = hex.length();
        byte[] datos = new byte[longitud / 2];
        for (int i = 0; i < longitud; i += 2) {
            datos[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i + 1), 16));
        }
        return datos;
    }
}
