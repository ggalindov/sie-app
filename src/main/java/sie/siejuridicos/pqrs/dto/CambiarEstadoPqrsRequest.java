package sie.siejuridicos.pqrs.dto;

import jakarta.validation.constraints.NotNull;
import sie.siejuridicos.pqrs.EstadoPqrs;

public record CambiarEstadoPqrsRequest(
        @NotNull(message = "El nuevo estado es obligatorio")
        EstadoPqrs nuevoEstado
) {
}
