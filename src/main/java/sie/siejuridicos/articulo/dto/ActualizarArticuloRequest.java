package sie.siejuridicos.articulo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import sie.siejuridicos.articulo.EstadoArticulo;
import sie.siejuridicos.articulo.TipoContenido;

// El mismo endpoint (PUT) sirve para editar y para publicar: si estado=PUBLICADO,
// el servicio invoca fn_publicar_articulo para aplicar la validación de integridad.
// titulo/resumen acotados a las columnas VARCHAR(200)/VARCHAR(500) de `articulos`
// (V3__crear_tabla_articulos.sql); contenido queda sin límite a propósito, es TEXT y
// el HTML rico de Tiptap puede ser legítimamente extenso.
public record ActualizarArticuloRequest(
        @NotBlank(message = "El título es obligatorio")
        @Size(max = 200, message = "El título no puede superar los 200 caracteres")
        String titulo,

        @NotBlank(message = "El contenido es obligatorio")
        String contenido,

        @Size(max = 500, message = "El resumen no puede superar los 500 caracteres")
        String resumen,

        @Size(max = 500, message = "El enlace de la imagen no puede superar los 500 caracteres")
        @Pattern(regexp = "^(https?://.+)?$", message = "El enlace de la imagen debe empezar por http:// o https://")
        String imagenUrl,

        @NotNull(message = "El tipo de contenido es obligatorio")
        TipoContenido tipoContenido,

        @NotNull(message = "La categoría es obligatoria")
        Long idCategoria,

        @Positive(message = "El tiempo de lectura debe ser mayor a cero")
        Integer tiempoLecturaMin,

        @NotNull(message = "El estado es obligatorio")
        EstadoArticulo estado
) {
}
