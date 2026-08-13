-- Testimonios enviados por clientes desde el sitio público. Quedan en PENDIENTE
-- hasta que un usuario interno los apruebe; solo los APROBADO se muestran en la web.
CREATE TABLE testimonios_publicos
(
    id               BIGSERIAL PRIMARY KEY,
    nombre           VARCHAR(150) NOT NULL,
    empresa          VARCHAR(150),
    cargo            VARCHAR(150),
    cita             VARCHAR(600) NOT NULL,
    calificacion     SMALLINT     NOT NULL DEFAULT 5,
    correo           VARCHAR(150) NOT NULL,
    estado           VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE',
    fecha_creacion   TIMESTAMP    NOT NULL DEFAULT now(),
    fecha_moderacion TIMESTAMP,
    CONSTRAINT ck_testimonios_publicos_estado CHECK (estado IN ('PENDIENTE', 'APROBADO', 'RECHAZADO')),
    CONSTRAINT ck_testimonios_publicos_calificacion CHECK (calificacion BETWEEN 1 AND 5)
);

CREATE INDEX ix_testimonios_publicos_estado ON testimonios_publicos (estado);
