package sie.siejuridicos.whatsapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Pattern;

// Envía la notificación del radicado por WhatsApp (línea de atención de la firma) usando la
// API oficial de Meta (WhatsApp Cloud API), NUNCA la app de WhatsApp Business normal -- esa
// no tiene forma de conectarse por código, solo la Cloud API (o un intermediario tipo
// Twilio) permite enviar mensajes de negocio mediante una llamada HTTP.
//
// SOLO se pueden enviar mensajes de PLANTILLA (no texto libre) cuando el negocio inicia la
// conversación primero -- regla dura de Meta/WhatsApp, no una limitación nuestra: la
// plantilla se redacta una vez y se manda a aprobación de Meta (ver DEPLOY.md), y desde
// entonces esta clase solo rellena sus variables (nombre, radicado, enlace), nunca compone
// el texto libremente como si fuera un correo.
//
// Mismo criterio que EmailService: @Async, atrapa sus propios errores, nunca hace fallar la
// operación principal (crear/sincronizar un caso) ni bloquea la respuesta HTTP mientras
// espera a la API de Meta. Igual que HojaCalculoService, responde con gracia si no está
// configurado (WHATSAPP_ACCESS_TOKEN/WHATSAPP_PHONE_NUMBER_ID vacíos): el resto del sistema
// sigue funcionando con solo el correo hasta que se configure.
@Service
public class WhatsAppService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppService.class);

    private static final String VERSION_API = "v21.0";
    // Un número colombiano real: 10 dígitos empezando en 3 (celular), con o sin el
    // indicativo de país 57 ya puesto. Cualquier otra cosa (vacío, con letras, de otro país
    // sin el indicativo puesto) se descarta antes de intentar enviar -- mejor no enviar nada
    // que enviarle un mensaje de WhatsApp de negocio a un número inválido o equivocado.
    private static final Pattern CELULAR_COLOMBIANO = Pattern.compile("^3\\d{9}$");

    private final HttpClient clienteHttp = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final String accessToken;
    private final String phoneNumberId;
    private final String nombrePlantilla;
    private final String nombrePlantillaCobro;
    private final String nombrePlantillaSolicitud;
    private final String codigoIdiomaPlantilla;
    private final String sitioWeb;
    // A dónde llega el aviso de "nueva solicitud" (ver enviarNotificacionAdminNuevaSolicitud):
    // un número interno de la firma, no el del cliente que escribió -- pedido explícito del
    // usuario ("que le llegue reporte a ese número de todo"), ya normalizado a E.164 sin "+"
    // al arrancar (mismo formato que exige la API de Meta) para no tener que renormalizarlo
    // en cada envío.
    private final String numeroAdminNotificaciones;
    private final boolean configurado;

    public WhatsAppService(
            @Value("${app.whatsapp.access-token:}") String accessToken,
            @Value("${app.whatsapp.phone-number-id:}") String phoneNumberId,
            @Value("${app.whatsapp.plantilla-nombre:notificacion_radicado}") String nombrePlantilla,
            @Value("${app.whatsapp.plantilla-cobro-nombre:recordatorio_cobro}") String nombrePlantillaCobro,
            @Value("${app.whatsapp.plantilla-solicitud-nombre:nueva_solicitud}") String nombrePlantillaSolicitud,
            @Value("${app.whatsapp.plantilla-idioma:es}") String codigoIdiomaPlantilla,
            @Value("${app.whatsapp.admin-numero:+573124781583}") String numeroAdminNotificaciones,
            @Value("${app.firma.sitio-web}") String sitioWeb) {
        this.accessToken = accessToken;
        this.phoneNumberId = phoneNumberId;
        this.nombrePlantilla = nombrePlantilla;
        this.nombrePlantillaCobro = nombrePlantillaCobro;
        this.nombrePlantillaSolicitud = nombrePlantillaSolicitud;
        this.codigoIdiomaPlantilla = codigoIdiomaPlantilla;
        this.sitioWeb = sitioWeb;
        this.numeroAdminNotificaciones = normalizarCelular(numeroAdminNotificaciones);
        this.configurado = !accessToken.isBlank() && !phoneNumberId.isBlank();
        if (!configurado) {
            log.warn("WhatsApp Cloud API no configurado (faltan WHATSAPP_ACCESS_TOKEN / "
                    + "WHATSAPP_PHONE_NUMBER_ID): la notificación de radicado se enviará solo por "
                    + "correo hasta que se configure.");
        }
        if (this.numeroAdminNotificaciones == null) {
            log.warn("app.whatsapp.admin-numero ('{}') no es un celular colombiano reconocible -- "
                    + "el aviso de nuevas solicitudes por WhatsApp no se podrá enviar.", numeroAdminNotificaciones);
        }
    }

    public boolean isConfigurado() {
        return configurado;
    }

    // Normaliza a formato E.164 sin "+" (lo que exige la API de Meta: solo dígitos,
    // indicativo de país incluido). Devuelve null si el número no se puede reconocer como un
    // celular colombiano real -- ver CELULAR_COLOMBIANO.
    public static String normalizarCelular(String telefono) {
        if (telefono == null) {
            return null;
        }
        String soloDigitos = telefono.replaceAll("[^0-9]", "");
        // quita un indicativo "57" ya puesto para dejar los 10 dígitos crudos, y así aplicar
        // una sola validación sin importar si la hoja lo trae con o sin indicativo.
        String celular = soloDigitos.startsWith("57") && soloDigitos.length() == 12
                ? soloDigitos.substring(2)
                : soloDigitos;
        if (!CELULAR_COLOMBIANO.matcher(celular).matches()) {
            return null;
        }
        return "57" + celular;
    }

    // No lanza excepción nunca (mismo criterio que EmailService.enviarHtml): un fallo de
    // WhatsApp no debe tumbar la sincronización ni el envío de correos de los demás casos.
    @Async
    public void enviarCodigoCaso(String nombreCliente, String telefono, String radicadoId) {
        enviarCodigoCasoSincrono(nombreCliente, telefono, radicadoId);
    }

    // Variante síncrona (devuelve si de verdad se envió), misma razón que
    // EmailService.enviarCodigoCasoSincrono: la usa el envío masivo controlado de
    // CasoService.enviarCorreosPendientes(), uno por uno con pausa entre cada uno.
    public boolean enviarCodigoCasoSincrono(String nombreCliente, String telefono, String radicadoId) {
        if (!configurado) {
            return false;
        }
        // Última barrera antes de mandar nada, sin importar quién llame este método: jamás se
        // envía un WhatsApp de "tu radicado es..." sin un radicado real.
        if (radicadoId == null || radicadoId.isBlank()) {
            log.warn("Se intentó enviar el WhatsApp de radicado sin un radicado real -- se canceló el envío.");
            return false;
        }
        String celular = normalizarCelular(telefono);
        if (celular == null) {
            log.warn("No se pudo enviar WhatsApp: el teléfono guardado no es un celular "
                    + "colombiano reconocible.");
            return false;
        }
        try {
            String cuerpo = construirCuerpoPlantilla(celular, nombreCliente, radicadoId);
            HttpRequest solicitud = HttpRequest.newBuilder()
                    .uri(URI.create("https://graph.facebook.com/" + VERSION_API + "/" + phoneNumberId + "/messages"))
                    .timeout(Duration.ofSeconds(8))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                    .build();
            HttpResponse<String> respuesta = clienteHttp.send(solicitud, HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() >= 300) {
                // El cuerpo de la respuesta de error de Meta no se loguea completo (puede
                // repetir el número de teléfono que se mandó): solo el código, suficiente
                // para diagnosticar (plantilla no aprobada, token vencido, número inválido).
                log.warn("Meta respondió {} al enviar la notificación de WhatsApp del radicado {}",
                        respuesta.statusCode(), radicadoId);
                return false;
            }
            return true;
        } catch (Exception ex) {
            log.warn("No se pudo enviar la notificación de WhatsApp del radicado {}: {}",
                    radicadoId, ex.getMessage());
            return false;
        }
    }

    // Recordatorio mensual de cobro (ver cobro.CobroService.enviarRecordatorios): usa una
    // plantilla DISTINTA a la de radicado, aprobada aparte en Meta, con dos botones de
    // respuesta rápida (Sí/No) ya definidos dentro de la plantilla misma -- por eso el
    // cuerpo que se manda aquí no necesita describir los botones, solo rellena las variables
    // de texto del cuerpo del mensaje (nombre, monto). La respuesta del cliente llega después
    // por separado, al webhook (ver WhatsAppWebhookController), nunca en esta misma llamada.
    @Async
    public void enviarRecordatorioCobro(String nombreCliente, String telefono, String honorariosTexto) {
        enviarRecordatorioCobroSincrono(nombreCliente, telefono, honorariosTexto);
    }

    // Variante síncrona (devuelve si de verdad se envió), usada por el envío masivo
    // controlado de CobroService.enviarRecordatorios(), uno por uno con pausa entre cada uno.
    public boolean enviarRecordatorioCobroSincrono(String nombreCliente, String telefono, String honorariosTexto) {
        if (!configurado) {
            return false;
        }
        String celular = normalizarCelular(telefono);
        if (celular == null) {
            log.warn("No se pudo enviar el recordatorio de cobro por WhatsApp: el teléfono guardado no "
                    + "es un celular colombiano reconocible.");
            return false;
        }
        try {
            String cuerpo = construirCuerpoPlantillaCobro(celular, nombreCliente, honorariosTexto);
            HttpRequest solicitud = HttpRequest.newBuilder()
                    .uri(URI.create("https://graph.facebook.com/" + VERSION_API + "/" + phoneNumberId + "/messages"))
                    .timeout(Duration.ofSeconds(8))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                    .build();
            HttpResponse<String> respuesta = clienteHttp.send(solicitud, HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() >= 300) {
                log.warn("Meta respondió {} al enviar el recordatorio de cobro por WhatsApp", respuesta.statusCode());
                return false;
            }
            return true;
        } catch (Exception ex) {
            log.warn("No se pudo enviar el recordatorio de cobro por WhatsApp: {}", ex.getMessage());
            return false;
        }
    }

    // Aviso interno (no al cliente): apenas llega una solicitud nueva del formulario público,
    // le llega un WhatsApp con el resumen completo a la línea interna de la firma (ver
    // numeroAdminNotificaciones), además del correo que ya se le manda al admin (ver
    // EmailService.enviarNotificacionAdminNuevaSolicitud) -- pedido explícito del usuario.
    // Plantilla DISTINTA a las de radicado/cobro, aprobada aparte en Meta.
    @Async
    public void enviarNotificacionAdminNuevaSolicitud(String nombreCliente, String correoCliente,
                                                        String telefonoCliente, String mensaje) {
        if (!configurado || numeroAdminNotificaciones == null) {
            return;
        }
        try {
            String cuerpo = construirCuerpoPlantillaSolicitud(nombreCliente, correoCliente, telefonoCliente, mensaje);
            HttpRequest solicitud = HttpRequest.newBuilder()
                    .uri(URI.create("https://graph.facebook.com/" + VERSION_API + "/" + phoneNumberId + "/messages"))
                    .timeout(Duration.ofSeconds(8))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                    .build();
            HttpResponse<String> respuesta = clienteHttp.send(solicitud, HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() >= 300) {
                log.warn("Meta respondió {} al enviar el aviso de nueva solicitud por WhatsApp", respuesta.statusCode());
            }
        } catch (Exception ex) {
            log.warn("No se pudo enviar el aviso de nueva solicitud por WhatsApp: {}", ex.getMessage());
        }
    }

    private String construirCuerpoPlantillaSolicitud(String nombreCliente, String correoCliente,
                                                       String telefonoCliente, String mensaje) {
        String telefonoTexto = (telefonoCliente == null || telefonoCliente.isBlank())
                ? "(no proporcionado)" : telefonoCliente;
        return """
                {
                  "messaging_product": "whatsapp",
                  "to": "%s",
                  "type": "template",
                  "template": {
                    "name": "%s",
                    "language": { "code": "%s" },
                    "components": [
                      {
                        "type": "body",
                        "parameters": [
                          { "type": "text", "text": "%s" },
                          { "type": "text", "text": "%s" },
                          { "type": "text", "text": "%s" },
                          { "type": "text", "text": "%s" }
                        ]
                      }
                    ]
                  }
                }
                """.formatted(
                numeroAdminNotificaciones,
                nombrePlantillaSolicitud,
                codigoIdiomaPlantilla,
                escaparJson(nombreCliente),
                escaparJson(correoCliente),
                escaparJson(telefonoTexto),
                escaparJson(mensaje)
        );
    }

    private String construirCuerpoPlantillaCobro(String celular, String nombreCliente, String honorariosTexto) {
        return """
                {
                  "messaging_product": "whatsapp",
                  "to": "%s",
                  "type": "template",
                  "template": {
                    "name": "%s",
                    "language": { "code": "%s" },
                    "components": [
                      {
                        "type": "body",
                        "parameters": [
                          { "type": "text", "text": "%s" },
                          { "type": "text", "text": "%s" }
                        ]
                      }
                    ]
                  }
                }
                """.formatted(
                celular,
                nombrePlantillaCobro,
                codigoIdiomaPlantilla,
                escaparJson(nombreCliente),
                escaparJson(honorariosTexto)
        );
    }

    // JSON armado a mano (sin Jackson) a propósito: son 3 valores de texto sencillos, y así
    // este servicio no depende de que el ObjectMapper del proyecto (Jackson 3, ver notas de
    // Spring Boot 4 en CLAUDE.md) esté configurado de una forma compatible -- una llamada
    // aislada, más fácil de razonar que agregar una dependencia más a esta ruta.
    private String construirCuerpoPlantilla(String celular, String nombreCliente, String radicadoId) {
        String enlace = sitioWeb + "/consulta-caso";
        return """
                {
                  "messaging_product": "whatsapp",
                  "to": "%s",
                  "type": "template",
                  "template": {
                    "name": "%s",
                    "language": { "code": "%s" },
                    "components": [
                      {
                        "type": "body",
                        "parameters": [
                          { "type": "text", "text": "%s" },
                          { "type": "text", "text": "%s" },
                          { "type": "text", "text": "%s" }
                        ]
                      }
                    ]
                  }
                }
                """.formatted(
                celular,
                nombrePlantilla,
                codigoIdiomaPlantilla,
                escaparJson(nombreCliente),
                escaparJson(radicadoId),
                escaparJson(enlace)
        );
    }

    private static String escaparJson(String texto) {
        return texto == null ? "" : texto
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ");
    }
}
