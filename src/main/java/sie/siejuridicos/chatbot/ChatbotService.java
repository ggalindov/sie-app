package sie.siejuridicos.chatbot;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import sie.siejuridicos.chatbot.dto.MensajeChatbotRequest;
import sie.siejuridicos.chatbot.dto.MensajeChatbotResponse;
import sie.siejuridicos.chatbot.dto.TurnoChatDto;
import sie.siejuridicos.common.exception.LimiteChatbotExcedidoException;
import sie.siejuridicos.common.exception.RecursoNoEncontradoException;

import java.util.ArrayList;
import java.util.List;

@Service
public class ChatbotService {

    private static final String PROMPT_SISTEMA = """
            Eres el asistente virtual de atención inicial de SIE Jurídicos, una firma de abogados. Tu propósito es:
            1. Responder preguntas frecuentes sobre horarios de atención, ubicación, áreas de práctica \
            (Laboral, Familia, Civil, Mercantil, Administrativo, Constitucional) y el proceso general de contacto con la firma.
            2. Identificar el motivo de contacto del usuario. Si el usuario quiere que un abogado lo contacte, \
            pídele su nombre y correo electrónico (el teléfono es opcional) antes de registrar la solicitud.
            3. En cuanto tengas nombre, correo y el motivo de la consulta, usa la herramienta de registro de solicitud \
            para dejarla registrada en el panel administrativo, y confírmale al usuario que el equipo lo contactará pronto.
            4. Nunca emitas asesoría jurídica de fondo ni interpretes o opines sobre el caso particular del usuario. \
            Si el usuario necesita asesoría específica, indícale amablemente que un abogado de la firma debe revisar \
            su caso, y ofrécele continuar la conversación por WhatsApp o agendar una cita.
            5. Mantén un tono profesional, cálido y breve.
            """;

    private final ConversacionChatbotRepository conversacionChatbotRepository;
    private final ChatClient chatClient;
    private final ChatbotTools chatbotTools;
    private final int limiteMensual;

    public ChatbotService(ConversacionChatbotRepository conversacionChatbotRepository,
                           ChatClient.Builder chatClientBuilder,
                           ChatbotTools chatbotTools,
                           @Value("${app.chatbot.limite-mensual}") int limiteMensual) {
        this.conversacionChatbotRepository = conversacionChatbotRepository;
        this.chatClient = chatClientBuilder.defaultSystem(PROMPT_SISTEMA).build();
        this.chatbotTools = chatbotTools;
        this.limiteMensual = limiteMensual;
    }

    // Sin @Transactional a nivel de método a propósito: la llamada a Claude puede tardar
    // varios segundos y no debe mantener abierta una transacción/conexión de base de datos
    // mientras espera esa respuesta de red. Cada operación de repositorio (findById, save,
    // y el @Transactional propio de ChatbotTools.registrarSolicitud) maneja su propia
    // transacción corta de forma independiente.
    public MensajeChatbotResponse responder(MensajeChatbotRequest request) {
        ConversacionChatbot conversacion = obtenerOCrearConversacion(request.conversacionId());

        List<Message> mensajes = construirHistorial(request.historial());
        mensajes.add(new UserMessage(request.mensaje()));

        String respuesta = chatClient.prompt()
                .messages(mensajes)
                .tools(chatbotTools)
                .call()
                .content();

        conversacion.setCantidadMensajes(conversacion.getCantidadMensajes() + 1);
        conversacionChatbotRepository.save(conversacion);

        return new MensajeChatbotResponse(conversacion.getId(), respuesta);
    }

    private ConversacionChatbot obtenerOCrearConversacion(Long conversacionId) {
        if (conversacionId != null) {
            return conversacionChatbotRepository.findById(conversacionId)
                    .orElseThrow(() -> new RecursoNoEncontradoException("No existe la conversación con id " + conversacionId));
        }

        // RF-27: solo se valida el tope al iniciar una conversación nueva, no en cada mensaje,
        // ya que cada fila de conversaciones_chatbot representa una conversación completa.
        if (conversacionChatbotRepository.contarConversacionesMesActual() >= limiteMensual) {
            throw new LimiteChatbotExcedidoException(
                    "Se alcanzó el límite mensual de conversaciones del chatbot. Por favor contáctenos por WhatsApp.");
        }

        ConversacionChatbot nueva = new ConversacionChatbot();
        nueva.setCantidadMensajes(0);
        return conversacionChatbotRepository.save(nueva);
    }

    private List<Message> construirHistorial(List<TurnoChatDto> historial) {
        List<Message> mensajes = new ArrayList<>();
        if (historial == null) {
            return mensajes;
        }
        for (TurnoChatDto turno : historial) {
            Message mensaje = "ASISTENTE".equals(turno.rol())
                    ? new AssistantMessage(turno.contenido())
                    : new UserMessage(turno.contenido());
            mensajes.add(mensaje);
        }
        return mensajes;
    }
}
