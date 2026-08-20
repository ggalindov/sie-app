package sie.siejuridicos.articulo.dto;

import sie.siejuridicos.articulo.Articulo;
import sie.siejuridicos.articulo.TipoContenido;
import sie.siejuridicos.categoria.dto.CategoriaResponse;

import java.time.LocalDateTime;

// Vista liviana para listados públicos (RF-11): sin el contenido completo del artículo.
public record ArticuloResumenResponse(
        Long id,
        String titulo,
        String slug,
        String resumen,
        String imagenUrl,
        TipoContenido tipoContenido,
        CategoriaResponse categoria,
        String autorNombre,
        LocalDateTime fechaPublicacion,
        Integer tiempoLecturaMin
) {
    public static ArticuloResumenResponse desde(Articulo articulo) {
        return new ArticuloResumenResponse(
                articulo.getId(),
                articulo.getTitulo(),
                articulo.getSlug(),
                articulo.getResumen(),
                articulo.getImagenUrl(),
                articulo.getTipoContenido(),
                CategoriaResponse.desde(articulo.getCategoria()),
                articulo.getAutor().getNombre(),
                articulo.getFechaPublicacion(),
                articulo.getTiempoLecturaMin()
        );
    }
}
