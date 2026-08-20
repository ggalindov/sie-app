package sie.siejuridicos.faq.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import sie.siejuridicos.faq.EstadoPreguntaFrecuente;

public record ModerarPreguntaFrecuenteRequest(
        @NotNull EstadoPreguntaFrecuente nuevoEstado,

        @Size(max = 3000, message = "La respuesta no puede superar los 3000 caracteres")
        String respuesta
) {
}
