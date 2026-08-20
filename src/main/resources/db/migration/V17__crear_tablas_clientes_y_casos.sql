-- Consulta de estado de caso sin cuenta de usuario (ver decisión de "sin registro
-- público"): el abogado/admin da de alta al cliente y su caso desde el panel, el sistema
-- genera un código único que se envía por correo, y el cliente lo consulta en una página
-- pública aparte con solo ese código, sin login. Un mismo cliente (identificado por
-- correo) puede tener varios casos a lo largo del tiempo, de ahí la tabla separada.
CREATE TABLE clientes
(
    id             BIGSERIAL PRIMARY KEY,
    nombre         VARCHAR(150) NOT NULL,
    correo         VARCHAR(150) NOT NULL,
    telefono       VARCHAR(30),
    fecha_creacion TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uq_clientes_correo UNIQUE (correo)
);

-- tipo de caso reutiliza las mismas categorías/áreas de práctica del blog (categorias):
-- son exactamente las áreas que maneja la firma, no tiene sentido mantener dos listas.
CREATE TABLE casos
(
    id               BIGSERIAL PRIMARY KEY,
    id_cliente       BIGINT       NOT NULL REFERENCES clientes (id),
    id_categoria     BIGINT       NOT NULL REFERENCES categorias (id),
    codigo_unico     VARCHAR(20)  NOT NULL,
    etapa            VARCHAR(30)  NOT NULL DEFAULT 'RADICADO',
    notas_internas   TEXT,
    fecha_creacion   TIMESTAMP    NOT NULL DEFAULT now(),
    fecha_actualizacion TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_casos_codigo_unico UNIQUE (codigo_unico),
    CONSTRAINT ck_casos_etapa CHECK (etapa IN
        ('RADICADO', 'EN_ESTUDIO', 'EN_TRAMITE', 'AUDIENCIA_DILIGENCIA', 'RESUELTO'))
);

CREATE INDEX ix_casos_codigo_unico ON casos (codigo_unico);
CREATE INDEX ix_casos_id_cliente ON casos (id_cliente);
