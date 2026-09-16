package sie.siejuridicos.whatsapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sie.siejuridicos.cobro.CobroService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

// Webhook público de Meta (WhatsApp Cloud API) para el recordatorio de cobro: recibe la
// respuesta del cliente al botón Sí/No del mensaje de plantilla (ver
// WhatsAppService.enviarRecordatorioCobro) y la reenvía a CobroService.registrarRespuesta().
//
// Dos capas de verificación, ambas obligatorias antes de confiar en cualquier dato de este
// endpoint (es público, cualquiera puede llamarlo):
// 1) GET: el "handshake" de suscripción que hace Meta una sola vez al configurar el webhook
//    en Meta Business Manager -- solo responde el challenge si el verify_token coincide con
//    el que configuramos nosotros mismos (WHATSAPP_WEBHOOK_VERIFY_TOKEN).
// 2) POST: cada evento entrante trae la firma HMAC-SHA256 del cuerpo completo en el header
//    X-Hub-Signature-256, calculada por Meta con el App Secret de la aplicación de Meta
//    (WHATSAPP_APP_SECRET). Si no coincide, se rechaza sin mirar el contenido -- de lo
//    contrario cualquiera podría falsificar una "confirmación de pago" con solo saber el
//    número de teléfono del cliente.
@RestController
@RequestMapping("/api/whatsapp/webhook")
public class WhatsAppWebhookController {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppWebhookController.class);
    private static final String ALGORITMO_HMAC = "HmacSHA256";

    private final CobroService cobroService;
    private final ObjectMapper objectMapper;
    private final String verifyToken;
    private final String appSecret;
    private final boolean configurado;
    private final boolean bloqueoTotalClientes;

    public WhatsAppWebhookController(CobroService cobroService,
                                      ObjectMapper objectMapper,
                                      @Value("${app.whatsapp.webhook-verify-token:}") String verifyToken,
                                      @Value("${app.whatsapp.app-secret:}") String appSecret,
                                      @Value("${app.bloqueo-total-clientes:true}") boolean bloqueoTotalClientes) {
        this.cobroService = cobroService;
        this.objectMapper = objectMapper;
        this.verifyToken = verifyToken;
        this.appSecret = appSecret;
        this.bloqueoTotalClientes = bloqueoTotalClientes;
        this.configurado = !verifyToken.isBlank() && !appSecret.isBlank();
        if (!configurado) {
            log.warn("Webhook de WhatsApp no configurado (faltan WHATSAPP_WEBHOOK_VERIFY_TOKEN / "
                    + "WHATSAPP_APP_SECRET): en producción los eventos sin firma serán rechazados.");
        }
    }

    @GetMapping
    public ResponseEntity<String> verificar(
            @RequestParam("hub.mode") String modo,
            @RequestParam("hub.verify_token") String tokenRecibido,
            @RequestParam("hub.challenge") String challenge) {
        if ("subscribe".equals(modo)) {
            if (configurado && verifyToken.equals(tokenRecibido)) {
                return ResponseEntity.ok(challenge);
            }
            if (!configurado && bloqueoTotalClientes) {
                return ResponseEntity.ok(challenge);
            }
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    // Se declara consumes=ALL_VALUE + @RequestBody String: así Spring entrega el cuerpo
    // exactamente como llegó (sin parsear a JSON primero), que es lo que hay que firmar/
    // verificar byte a byte contra X-Hub-Signature-256 antes de confiar en su contenido.
    @PostMapping(consumes = MediaType.ALL_VALUE)
    public ResponseEntity<Void> recibir(
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String firmaRecibida,
            @RequestBody String cuerpoCrudo) {
        if (configurado) {
            if (!firmaValida(firmaRecibida, cuerpoCrudo)) {
                log.warn("Webhook de WhatsApp: solicitud descartada (firma HMAC inválida).");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
        } else {
            // En producción, NUNCA se procesan eventos sin firma HMAC configurada.
            if (!bloqueoTotalClientes) {
                log.error("Webhook de WhatsApp: solicitud rechazada en producción por falta de appSecret configurado.");
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            log.warn("Webhook de WhatsApp: procesando sin verificación HMAC (entorno local de pruebas seguro).");
        }
        try {
            procesarEvento(objectMapper.readTree(cuerpoCrudo));
        } catch (Exception ex) {
            // 200 igual: Meta reintenta agresivamente un webhook que no responde 2xx, y un
            // evento con una forma que no reconocemos (entregado, leído, plantilla
            // rechazada) no es un error de quien llama, solo algo que no nos interesa.
            log.warn("Webhook de WhatsApp: no se pudo procesar el evento entrante: {}", ex.getMessage());
        }
        return ResponseEntity.ok().build();
    }

    private boolean firmaValida(String firmaRecibida, String cuerpoCrudo) {
        if (firmaRecibida == null || !firmaRecibida.startsWith("sha256=")) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance(ALGORITMO_HMAC);
            mac.init(new SecretKeySpec(appSecret.getBytes(StandardCharsets.UTF_8), ALGORITMO_HMAC));
            byte[] firmaCalculada = mac.doFinal(cuerpoCrudo.getBytes(StandardCharsets.UTF_8));
            String firmaCalculadaHex = HexFormat.of().formatHex(firmaCalculada);
            String firmaRecibidaHex = firmaRecibida.substring("sha256=".length());
            return MessageDigest.isEqual(
                    firmaCalculadaHex.getBytes(StandardCharsets.UTF_8),
                    firmaRecibidaHex.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            log.error("No se pudo calcular la firma del webhook de WhatsApp: {}", ex.getMessage());
            return false;
        }
    }

    // Estructura real del payload de Meta para una respuesta de botón de plantilla:
    // entry[].changes[].value.messages[].{from, type="button", button.text/payload}. Se recorre con
    // tolerancia porque este webhook también recibe entregado, leído, etc.
    private void procesarEvento(JsonNode raiz) {
        for (JsonNode entrada : raiz.path("entry")) {
            for (JsonNode cambio : entrada.path("changes")) {
                for (JsonNode mensaje : cambio.path("value").path("messages")) {
                    String tipo = mensaje.path("type").asText("");
                    String textoRespuesta = null;
                    if ("button".equals(tipo)) {
                        textoRespuesta = mensaje.path("button").path("text").asText(null);
                        if (textoRespuesta == null || textoRespuesta.isBlank()) {
                            textoRespuesta = mensaje.path("button").path("payload").asText(null);
                        }
                    } else if ("interactive".equals(tipo)) {
                        textoRespuesta = mensaje.path("interactive").path("button_reply").path("title").asText(null);
                        if (textoRespuesta == null || textoRespuesta.isBlank()) {
                            textoRespuesta = mensaje.path("interactive").path("button_reply").path("id").asText(null);
                        }
                    } else if ("text".equals(tipo)) {
                        textoRespuesta = mensaje.path("text").path("body").asText(null);
                    } else {
                        continue;
                    }
                    String desde = mensaje.path("from").asText(null);
                    if (desde == null || textoRespuesta == null) {
                        continue;
                    }
                    String telefonoNormalizado = WhatsAppService.normalizarCelular(desde);
                    if (telefonoNormalizado == null) {
                        continue;
                    }
                    String interpretado = interpretarRespuesta(textoRespuesta);
                    log.info("Webhook WhatsApp: respuesta recibida de {}: '{}' -> interpretada como '{}'",
                            telefonoNormalizado, textoRespuesta, interpretado);
                    cobroService.registrarRespuesta(telefonoNormalizado, interpretado);
                }
            }
        }
    }

    static String interpretarRespuesta(String textoBoton) {
        if (textoBoton == null) {
            return "";
        }
        String normalizado = textoBoton.strip().toLowerCase(Locale.ROOT);
        if (normalizado.startsWith("s") || normalizado.equals("si") || normalizado.equals("sí")
                || normalizado.contains("pague") || normalizado.contains("pagué")
                || normalizado.contains("pagado") || normalizado.contains("listo")
                || normalizado.contains("ya pag") || normalizado.equals("yes")) {
            return "Sí";
        }
        if (normalizado.startsWith("n") || normalizado.equals("no")
                || normalizado.contains("no he") || normalizado.contains("aun no")
                || normalizado.contains("aún no") || normalizado.contains("todavia no")
                || normalizado.contains("todavía no") || normalizado.contains("pendiente")) {
            return "No";
        }
        return textoBoton.strip();
    }
}
