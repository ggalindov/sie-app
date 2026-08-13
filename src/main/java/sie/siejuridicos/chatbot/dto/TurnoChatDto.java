package sie.siejuridicos.chatbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TurnoChatDto(
        @NotBlank
        @Pattern(regexp = "USUARIO|ASISTENTE", message = "El rol debe ser USUARIO o ASISTENTE")
        String rol,

        @NotBlank
        String contenido
) {
}
