package sie.siejuridicos.articulo.dto;

import sie.siejuridicos.articulo.Articulo;
import sie.siejuridicos.articulo.TipoContenido;
import sie.siejuridicos.categoria.dto.CategoriaResponse;
import sie.siejuridicos.common.FechasUtil;

import java.time.Instant;

// Vista liviana para listados públicos (RF-11): sin el contenido completo del artículo.
//
// fechaPublicacion como Instant, no LocalDateTime: ver el comentario de
// ArticuloDetalleResponse (bug real de horario, "arregla la hora de los blogs a hora de
// colombia" -- un LocalDateTime se serializa sin 'Z', Instant sí).
public record ArticuloResumenResponse(
        Long id,
        String titulo,
        String slug,
        String resumen,
        String imagenUrl,
        TipoContenido tipoContenido,
        CategoriaResponse categoria,
        String autorNombre,
        Instant fechaPublicacion,
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
                FechasUtil.aInstanteUtc(articulo.getFechaPublicacion()),
                articulo.getTiempoLecturaMin()
        );
    }
}
