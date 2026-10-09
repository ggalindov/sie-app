package sie.siejuridicos.solicitud.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResponderSolicitudRequest(
        @NotBlank(message = "El asunto es obligatorio")
        @Size(max = 150, message = "El asunto no puede superar los 150 caracteres")
        String asunto,

        @NotBlank(message = "El mensaje es obligatorio")
        @Size(max = 5000, message = "El mensaje no puede superar los 5000 caracteres")
        String mensaje
) {
}
