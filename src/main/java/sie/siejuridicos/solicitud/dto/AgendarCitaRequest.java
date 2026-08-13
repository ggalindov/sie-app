package sie.siejuridicos.solicitud.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record AgendarCitaRequest(
        @NotNull(message = "La fecha y hora de la cita son obligatorias")
        @Future(message = "La cita debe programarse en una fecha futura")
        LocalDateTime fechaHora
) {
}
