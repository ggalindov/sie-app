package sie.siejuridicos.articulo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import sie.siejuridicos.articulo.EstadoArticulo;

// El mismo endpoint (PUT) sirve para editar y para publicar: si estado=PUBLICADO,
// el servicio invoca fn_publicar_articulo para aplicar la validación de integridad.
public record ActualizarArticuloRequest(
        @NotBlank(message = "El título es obligatorio")
        String titulo,

        @NotBlank(message = "El contenido es obligatorio")
        String contenido,

        String resumen,

        @NotNull(message = "La categoría es obligatoria")
        Long idCategoria,

        @Positive(message = "El tiempo de lectura debe ser mayor a cero")
        Integer tiempoLecturaMin,

        @NotNull(message = "El estado es obligatorio")
        EstadoArticulo estado
) {
}
