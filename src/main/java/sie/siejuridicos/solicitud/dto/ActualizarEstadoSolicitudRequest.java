package sie.siejuridicos.solicitud.dto;

import jakarta.validation.constraints.NotNull;
import sie.siejuridicos.solicitud.EstadoSolicitud;

public record ActualizarEstadoSolicitudRequest(
        @NotNull(message = "El nuevo estado es obligatorio")
        EstadoSolicitud nuevoEstado
) {
}
