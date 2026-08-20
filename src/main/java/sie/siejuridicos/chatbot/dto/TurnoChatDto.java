package sie.siejuridicos.chatbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TurnoChatDto(
        @NotBlank
        @Pattern(regexp = "USUARIO|ASISTENTE", message = "El rol debe ser USUARIO o ASISTENTE")
        String rol,

        @NotBlank
        @Size(max = 2000, message = "El contenido del turno es demasiado largo")
        String contenido
) {
}
