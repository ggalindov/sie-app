package sie.siejuridicos.chatbot.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record MensajeChatbotRequest(
        // null en el primer mensaje: el backend crea la conversación y devuelve su id
        Long conversacionId,

        @NotBlank(message = "El mensaje es obligatorio")
        @Size(max = 2000, message = "El mensaje no puede superar los 2000 caracteres")
        String mensaje,

        // turnos previos de la conversación (el cliente mantiene el historial, el backend es sin
        // estado). Límite de tamaño para que un cliente malicioso no pueda mandar un historial
        // gigante en cada request y disparar llamadas carísimas/lentas al modelo.
        @Valid
        @Size(max = 40, message = "El historial de la conversación es demasiado largo")
        List<TurnoChatDto> historial
) {
}
