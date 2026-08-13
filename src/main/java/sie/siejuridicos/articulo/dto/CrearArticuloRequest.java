package sie.siejuridicos.articulo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CrearArticuloRequest(
        @NotBlank(message = "El título es obligatorio")
        String titulo,

        @NotBlank(message = "El contenido es obligatorio")
        String contenido,

        String resumen,

        @NotNull(message = "La categoría es obligatoria")
        Long idCategoria,

        @Positive(message = "El tiempo de lectura debe ser mayor a cero")
        Integer tiempoLecturaMin
) {
}
