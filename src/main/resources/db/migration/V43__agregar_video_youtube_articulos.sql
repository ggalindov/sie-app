-- =====================================================================
-- Migración V43: Agregar columna para video de YouTube en artículos de blog
-- =====================================================================

ALTER TABLE articulos
    ADD COLUMN video_youtube_url VARCHAR(500);

COMMENT ON COLUMN articulos.video_youtube_url IS 'Enlace opcional de video de YouTube para incrustar reproductor dentro del artículo';
