CREATE TABLE articulos
(
    id                  BIGSERIAL PRIMARY KEY,
    titulo              VARCHAR(200) NOT NULL,
    slug                VARCHAR(220) NOT NULL,
    contenido           TEXT         NOT NULL,
    resumen             VARCHAR(500),
    id_categoria        BIGINT       NOT NULL,
    id_autor            BIGINT       NOT NULL,
    estado              VARCHAR(20)  NOT NULL DEFAULT 'BORRADOR',
    fecha_creacion      TIMESTAMP    NOT NULL DEFAULT now(),
    fecha_publicacion   TIMESTAMP,
    tiempo_lectura_min  INTEGER,
    CONSTRAINT uq_articulos_slug UNIQUE (slug),
    CONSTRAINT ck_articulos_estado CHECK (estado IN ('BORRADOR', 'PUBLICADO')),
    CONSTRAINT fk_articulos_categoria FOREIGN KEY (id_categoria) REFERENCES categorias (id) ON DELETE RESTRICT,
    CONSTRAINT fk_articulos_autor FOREIGN KEY (id_autor) REFERENCES usuarios_internos (id) ON DELETE RESTRICT
);

CREATE INDEX ix_articulos_estado ON articulos (estado);
CREATE INDEX ix_articulos_categoria ON articulos (id_categoria);
CREATE INDEX ix_articulos_autor ON articulos (id_autor);
CREATE INDEX ix_articulos_fecha_publicacion ON articulos (fecha_publicacion);

-- columna derivada + índice GIN para búsqueda por palabra clave (RF-10)
ALTER TABLE articulos
    ADD COLUMN busqueda tsvector
        GENERATED ALWAYS AS (
            to_tsvector('spanish', titulo || ' ' || coalesce(resumen, '') || ' ' || contenido)
            ) STORED;

CREATE INDEX ix_articulos_busqueda ON articulos USING GIN (busqueda);
