package sie.siejuridicos.articulo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import sie.siejuridicos.articulo.TipoContenido;

public record CrearArticuloRequest(
        @NotBlank(message = "El título es obligatorio")
        @Size(max = 200, message = "El título no puede superar los 200 caracteres")
        String titulo,

        @NotBlank(message = "El contenido es obligatorio")
        String contenido,

        @Size(max = 500, message = "El resumen no puede superar los 500 caracteres")
        String resumen,

        @Size(max = 500, message = "El enlace de la imagen no puede superar los 500 caracteres")
        // Exige http(s):// o vacío: sin esto, el campo aceptaba literalmente cualquier
        // texto, incluido "javascript:..." o "data:text/html,..." que un <img src>
        // insertado tal cual en /blog (ver blog/page.tsx) podría intentar ejecutar en
        // navegadores viejos o usarse para trucos de phishing con un ícono falso.
        @Pattern(regexp = "^(https?://.+)?$", message = "El enlace de la imagen debe empezar por http:// o https://")
        String imagenUrl,

        @NotNull(message = "El tipo de contenido es obligatorio")
        TipoContenido tipoContenido,

        @NotNull(message = "La categoría es obligatoria")
        Long idCategoria,

        @Positive(message = "El tiempo de lectura debe ser mayor a cero")
        Integer tiempoLecturaMin
) {
}
