package sie.siejuridicos.chatbot.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record MensajeChatbotRequest(
        // null en el primer mensaje: el backend crea la conversación y devuelve su id
        Long conversacionId,

        @NotBlank(message = "El mensaje es obligatorio")
        String mensaje,

        // turnos previos de la conversación (el cliente mantiene el historial, el backend es sin estado)
        @Valid
        List<TurnoChatDto> historial
) {
}
