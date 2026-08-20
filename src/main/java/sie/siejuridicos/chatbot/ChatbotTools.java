package sie.siejuridicos.chatbot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sie.siejuridicos.common.exception.ApiException;
import sie.siejuridicos.common.exception.ErroresBaseDatos;
import sie.siejuridicos.solicitud.OrigenSolicitud;
import sie.siejuridicos.solicitud.SolicitudRepository;

import java.util.regex.Pattern;

// Herramienta que el modelo invoca cuando decide que ya reunió los datos suficientes
// del usuario (RF-24). La decisión de CUÁNDO llamarla es del modelo; aquí solo se
// valida y persiste, igual que en el flujo del formulario de contacto.
//
// A diferencia de POST /api/solicitudes, estos argumentos NO pasan por Bean Validation
// (@Valid solo aplica a @RequestBody de un controlador, no a parámetros de una
// herramienta de Spring AI): el modelo podría, por manipulación de la conversación,
// llamar esta herramienta con valores fuera de rango. Por eso se valida a mano aquí,
// con los mismos límites que CrearSolicitudRequest (reflejan las columnas VARCHAR de
// `solicitudes`, ver V4), en vez de dejar que un valor demasiado largo llegue crudo a
// fn_crear_solicitud y Postgres lo rechace con un error interno.
@Component
public class ChatbotTools {

    private static final Logger log = LoggerFactory.getLogger(ChatbotTools.class);

    private static final int MAX_NOMBRE = 150;
    private static final int MAX_CORREO = 150;
    private static final int MAX_TELEFONO = 30;
    private static final int MAX_MENSAJE = 5000;
    // Formato laxo a propósito (solo forma general, no busca ser RFC 5322 completo):
    // el objetivo es rechazar basura evidente antes de tocar la base de datos, la
    // validación de negocio real sigue siendo el @Email de CrearSolicitudRequest para
    // el formulario público.
    private static final Pattern CORREO_VALIDO = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final SolicitudRepository solicitudRepository;

    public ChatbotTools(SolicitudRepository solicitudRepository) {
        this.solicitudRepository = solicitudRepository;
    }

    @Tool(description = "Registra una solicitud de contacto (lead) en el panel administrativo de la firma. " +
            "Úsala únicamente cuando el usuario ya haya compartido su nombre, su correo electrónico y el motivo de su consulta. " +
            "No inventes ni asumas estos datos: solo llama a esta herramienta con información que el usuario haya proporcionado explícitamente.")
    @Transactional
    public String registrarSolicitud(
            @ToolParam(description = "Nombre completo del usuario") String nombre,
            @ToolParam(description = "Correo electrónico del usuario") String correo,
            @ToolParam(description = "Teléfono del usuario, si lo compartió; en caso contrario, cadena vacía") String telefono,
            @ToolParam(description = "Motivo o resumen breve de la consulta del usuario") String motivo) {
        String validacion = validar(nombre, correo, telefono, motivo);
        if (validacion != null) {
            log.warn("Argumentos inválidos del modelo para registrarSolicitud: {}", validacion);
            return "No se pudo registrar la solicitud: " + validacion + " Pídele al usuario ese dato de nuevo, "
                    + "o sugiérele continuar por WhatsApp.";
        }

        try {
            String telefonoNormalizado = (telefono == null || telefono.isBlank()) ? null : telefono;
            solicitudRepository.crearSolicitud(nombre, correo, telefonoNormalizado, motivo, OrigenSolicitud.CHATBOT.name());
            return "La solicitud fue registrada exitosamente. Confírmaselo al usuario e infórmale que el equipo lo contactará pronto.";
        } catch (DataAccessException ex) {
            RuntimeException traducida = ErroresBaseDatos.traducir(ex);
            log.warn("No se pudo registrar la solicitud desde el chatbot: {}", traducida.getMessage(), traducida);
            // Solo las excepciones propias (ApiException) tienen un mensaje ya pensado para
            // mostrarse al usuario. Cualquier otra cosa (error crudo de JDBC/Postgres, que
            // puede incluir detalles internos de la consulta o del esquema) se reemplaza por
            // un mensaje genérico: nunca debe llegarle al usuario un error de base de datos
            // sin filtrar a través del chat.
            String mensajeSeguro = traducida instanceof ApiException
                    ? traducida.getMessage()
                    : "ocurrió un problema inesperado";
            return "No fue posible registrar la solicitud automáticamente (" + mensajeSeguro + "). "
                    + "Sugiérele al usuario continuar por WhatsApp.";
        }
    }

    private String validar(String nombre, String correo, String telefono, String motivo) {
        if (nombre == null || nombre.isBlank()) {
            return "falta el nombre.";
        }
        if (nombre.length() > MAX_NOMBRE) {
            return "el nombre es demasiado largo.";
        }
        if (correo == null || !CORREO_VALIDO.matcher(correo).matches()) {
            return "el correo no es válido.";
        }
        if (correo.length() > MAX_CORREO) {
            return "el correo es demasiado largo.";
        }
        if (telefono != null && telefono.length() > MAX_TELEFONO) {
            return "el teléfono es demasiado largo.";
        }
        if (motivo == null || motivo.isBlank()) {
            return "falta el motivo de la consulta.";
        }
        if (motivo.length() > MAX_MENSAJE) {
            return "el motivo de la consulta es demasiado largo.";
        }
        return null;
    }
}
