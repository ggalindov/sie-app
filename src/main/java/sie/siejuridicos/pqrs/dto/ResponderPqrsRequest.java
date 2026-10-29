package sie.siejuridicos.pqrs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResponderPqrsRequest(
        @NotBlank(message = "La respuesta es obligatoria")
        @Size(max = 3000, message = "La respuesta no puede superar los 3000 caracteres")
        String respuesta
) {
}
