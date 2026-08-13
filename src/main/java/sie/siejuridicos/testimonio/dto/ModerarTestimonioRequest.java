package sie.siejuridicos.testimonio.dto;

import jakarta.validation.constraints.NotNull;
import sie.siejuridicos.testimonio.EstadoTestimonio;

public record ModerarTestimonioRequest(
        @NotNull(message = "El nuevo estado es obligatorio")
        EstadoTestimonio nuevoEstado
) {
}
