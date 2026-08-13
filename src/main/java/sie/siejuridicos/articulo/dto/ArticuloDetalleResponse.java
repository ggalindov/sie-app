package sie.siejuridicos.articulo.dto;

import sie.siejuridicos.articulo.Articulo;
import sie.siejuridicos.articulo.EstadoArticulo;
import sie.siejuridicos.categoria.dto.CategoriaResponse;

import java.time.LocalDateTime;

public record ArticuloDetalleResponse(
        Long id,
        String titulo,
        String slug,
        String contenido,
        String resumen,
        CategoriaResponse categoria,
        String autorNombre,
        EstadoArticulo estado,
        LocalDateTime fechaCreacion,
        LocalDateTime fechaPublicacion,
        Integer tiempoLecturaMin
) {
    public static ArticuloDetalleResponse desde(Articulo articulo) {
        return new ArticuloDetalleResponse(
                articulo.getId(),
                articulo.getTitulo(),
                articulo.getSlug(),
                articulo.getContenido(),
                articulo.getResumen(),
                CategoriaResponse.desde(articulo.getCategoria()),
                articulo.getAutor().getNombre(),
                articulo.getEstado(),
                articulo.getFechaCreacion(),
                articulo.getFechaPublicacion(),
                articulo.getTiempoLecturaMin()
        );
    }
}
