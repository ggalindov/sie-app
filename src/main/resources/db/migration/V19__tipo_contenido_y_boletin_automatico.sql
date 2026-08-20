-- Blog y Noticias comparten la misma tabla (mismos campos: título, contenido, resumen,
-- categoría, imagen, autor, fechas): tipo_contenido distingue un artículo de opinión/guía
-- (BLOG) de una noticia sobre novedades normativas de Colombia y el mundo (NOTICIA).
ALTER TABLE articulos ADD COLUMN tipo_contenido VARCHAR(20) NOT NULL DEFAULT 'BLOG';
ALTER TABLE articulos ADD CONSTRAINT ck_articulos_tipo_contenido CHECK (tipo_contenido IN ('BLOG', 'NOTICIA'));

-- El boletín deja de ser una composición manual del admin: ahora es un envío automático
-- diario (ver BoletinDiarioScheduler) con lo que se publicó ese día. La tabla creada en
-- V15 (asunto/cuerpo/id_usuario_envio, pensada para composición manual) ya no aplica; se
-- recrea como un log de auditoría de los envíos automáticos. Sin datos reales en producción
-- todavía (tabla nueva de esta misma sesión), así que un DROP+CREATE limpio es más claro
-- que ir acumulando columnas nullable sobre un diseño que ya no corresponde.
DROP TABLE boletines_enviados;

CREATE TABLE boletines_enviados
(
    id                      BIGSERIAL PRIMARY KEY,
    cantidad_publicaciones  INTEGER   NOT NULL,
    cantidad_destinatarios  INTEGER   NOT NULL,
    fecha_envio             TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX ix_boletines_enviados_fecha ON boletines_enviados (fecha_envio DESC);
