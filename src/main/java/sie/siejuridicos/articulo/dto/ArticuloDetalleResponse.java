package sie.siejuridicos.articulo.dto;

import sie.siejuridicos.articulo.Articulo;
import sie.siejuridicos.articulo.EstadoArticulo;
import sie.siejuridicos.articulo.TipoContenido;
import sie.siejuridicos.categoria.dto.CategoriaResponse;
import sie.siejuridicos.common.FechasUtil;

import java.time.Instant;

// fechaCreacion/fechaPublicacion van como Instant, no LocalDateTime: bug real de horario
// encontrado en esta revisión (reportado por el usuario, "arregla la hora de los blogs a
// hora de colombia"). Articulo.fechaCreacion/fechaPublicacion son LocalDateTime que en
// realidad representan un instante UTC (hibernate.jdbc.time_zone=UTC), pero Jackson
// serializa un LocalDateTime SIN ninguna 'Z'/offset -- el frontend (new Date(iso)) lo
// interpreta entonces como si YA estuviera en la zona local de quien lo procesa, corriendo
// la hora real hasta 5 horas (el offset de Bogotá) y, para artículos publicados de noche,
// mostrando el día siguiente. Instant sí serializa con 'Z' automáticamente (vía el módulo
// JSR-310 que Spring Boot ya trae), así que el valor que llega al frontend es inequívoco:
// el mismo instante real sin importar en qué zona horaria corra el servidor o el navegador
// que lo reciba.
public record ArticuloDetalleResponse(
        Long id,
        String titulo,
        String slug,
        String contenido,
        String resumen,
        String imagenUrl,
        TipoContenido tipoContenido,
        CategoriaResponse categoria,
        String autorNombre,
        EstadoArticulo estado,
        Instant fechaCreacion,
        Instant fechaPublicacion,
        Integer tiempoLecturaMin
) {
    public static ArticuloDetalleResponse desde(Articulo articulo) {
        return new ArticuloDetalleResponse(
                articulo.getId(),
                articulo.getTitulo(),
                articulo.getSlug(),
                articulo.getContenido(),
                articulo.getResumen(),
                articulo.getImagenUrl(),
                articulo.getTipoContenido(),
                CategoriaResponse.desde(articulo.getCategoria()),
                articulo.getAutor().getNombre(),
                articulo.getEstado(),
                FechasUtil.aInstanteUtc(articulo.getFechaCreacion()),
                FechasUtil.aInstanteUtc(articulo.getFechaPublicacion()),
                articulo.getTiempoLecturaMin()
        );
    }
}
