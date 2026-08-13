CREATE TABLE categorias
(
    id     BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    slug   VARCHAR(120) NOT NULL,
    CONSTRAINT uq_categorias_nombre UNIQUE (nombre),
    CONSTRAINT uq_categorias_slug UNIQUE (slug)
);
