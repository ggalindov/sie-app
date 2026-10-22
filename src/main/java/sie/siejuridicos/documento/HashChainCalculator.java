package sie.siejuridicos.documento;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

// Encadena cada DocumentoEvento con el anterior del mismo documento (ver
// DocumentoTimelineService, único punto donde se invoca): HMAC-SHA256 sobre todos los
// campos del evento MÁS el hash del evento anterior, con una clave secreta que solo vive
// en el servidor (derivada de app.documento.clave-maestra, ver
// DocumentoClaveMaestraService). Sin esa clave, un acceso directo de escritura a la base de
// datos no puede fabricar ni "reparar" un eslabón válido -- cualquier fila alterada o
// borrada rompe el encadenamiento a partir de ese punto, detectable por
// DocumentoEventoChainVerifier.
@Component
public class HashChainCalculator {

    // Raíz de la cadena: el "hash anterior" del primer evento de cada documento.
    public static final String GENESIS = "0".repeat(64);

    private static final String ALGORITMO = "HmacSHA256";

    private final byte[] claveSecreta;

    public HashChainCalculator(DocumentoClaveMaestraService claveMaestraService) {
        this.claveSecreta = claveMaestraService.claveCadenaEventos();
    }

    public String calcular(UUID documentoId, long secuencia, String hashEventoAnterior, String tipoEvento,
                            String actorTipo, Long staffId, UUID firmanteId, String descripcion, String ip,
                            String userAgent, String hashDocumentoEnEvento, LocalDateTime ocurridoEn) {
        String mensaje = String.join("|",
                String.valueOf(documentoId),
                String.valueOf(secuencia),
                hashEventoAnterior,
                nullSeguro(tipoEvento),
                nullSeguro(actorTipo),
                String.valueOf(staffId),
                String.valueOf(firmanteId),
                nullSeguro(descripcion),
                nullSeguro(ip),
                nullSeguro(userAgent),
                nullSeguro(hashDocumentoEnEvento),
                String.valueOf(ocurridoEn));
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(claveSecreta, ALGORITMO));
            byte[] firma = mac.doFinal(mensaje.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : firma) {
                hex.append(String.format(Locale.ROOT, "%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException("No se pudo calcular el eslabón de la cadena de eventos.", ex);
        }
    }

    private static String nullSeguro(String valor) {
        return valor == null ? "" : valor;
    }
}
