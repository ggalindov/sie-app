package sie.siejuridicos.documento;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.UUID;

// Calcula (y luego permite recomputar para confirmar) el código de verificación
// criptográfico que se estampa en cada documento sellado (ver DocumentoPdfStamper, que lo
// calcula al sellar, y VerificacionPublicaService, que lo recalcula al verificar). Es un
// HMAC-SHA256 sobre datos ya públicos del propio documento (id y número de secuencia, ambos
// asignados una sola vez y nunca modificados después) firmado con una clave secreta que
// solo vive en el servidor: nadie puede fabricar un código válido para un documento que SIE
// nunca emitió sin conocer esa clave, aunque los datos de entrada sean visibles.
@Component
public class CodigoVerificacionCalculator {

    private static final String ALGORITMO = "HmacSHA256";
    private static final int LONGITUD_CODIGO_HEX = 24;

    private final byte[] claveSecreta;

    public CodigoVerificacionCalculator(DocumentoClaveMaestraService claveMaestraService) {
        this.claveSecreta = claveMaestraService.claveCodigoVerificacion();
    }

    // Formato final legible en grupos de 4: "A1B2-C3D4-E5F6-A7B8-C9D0-E1F2".
    public String calcular(UUID documentoId, long numeroSecuencia) {
        String mensaje = documentoId + "|" + numeroSecuencia;
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(claveSecreta, ALGORITMO));
            byte[] firma = mac.doFinal(mensaje.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : firma) {
                hex.append(String.format(Locale.ROOT, "%02X", b));
                if (hex.length() >= LONGITUD_CODIGO_HEX) {
                    break;
                }
            }
            return agruparConGuiones(hex.substring(0, LONGITUD_CODIGO_HEX));
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("No se pudo calcular el código de verificación del documento.", ex);
        }
    }

    private static String agruparConGuiones(String hex) {
        StringBuilder agrupado = new StringBuilder();
        for (int i = 0; i < hex.length(); i += 4) {
            if (i > 0) {
                agrupado.append('-');
            }
            agrupado.append(hex, i, Math.min(i + 4, hex.length()));
        }
        return agrupado.toString();
    }
}
