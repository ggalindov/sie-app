package sie.siejuridicos.chatbot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sie.siejuridicos.common.exception.ErroresBaseDatos;
import sie.siejuridicos.solicitud.OrigenSolicitud;
import sie.siejuridicos.solicitud.SolicitudRepository;

// Herramienta que el modelo invoca cuando decide que ya reunió los datos suficientes
// del usuario (RF-24). La decisión de CUÁNDO llamarla es del modelo; aquí solo se
// valida y persiste, igual que en el flujo del formulario de contacto.
@Component
public class ChatbotTools {

    private static final Logger log = LoggerFactory.getLogger(ChatbotTools.class);

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
        try {
            String telefonoNormalizado = (telefono == null || telefono.isBlank()) ? null : telefono;
            solicitudRepository.crearSolicitud(nombre, correo, telefonoNormalizado, motivo, OrigenSolicitud.CHATBOT.name());
            return "La solicitud fue registrada exitosamente. Confírmaselo al usuario e infórmale que el equipo lo contactará pronto.";
        } catch (DataAccessException ex) {
            RuntimeException traducida = ErroresBaseDatos.traducir(ex);
            log.warn("No se pudo registrar la solicitud desde el chatbot: {}", traducida.getMessage());
            return "No fue posible registrar la solicitud automáticamente (" + traducida.getMessage() + "). "
                    + "Sugiérele al usuario continuar por WhatsApp.";
        }
    }
}
