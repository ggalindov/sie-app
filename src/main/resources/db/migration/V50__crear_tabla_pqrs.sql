-- PQRS (Peticiones, Quejas, Reclamos y Sugerencias) enviadas desde el sitio público.
-- Cada registro se identifica por un radicado único que el remitente usa para consultar
-- el estado sin necesidad de crear una cuenta (mismo patrón que la consulta de casos,
-- ver CasoPublicoController/ConsultaCasoAbuseGuard).
CREATE TABLE pqrs
(
    id                     BIGSERIAL PRIMARY KEY,
    radicado               VARCHAR(30)  NOT NULL UNIQUE,
    tipo                   VARCHAR(20)  NOT NULL,
    nombre                 VARCHAR(150) NOT NULL,
    correo                 VARCHAR(150) NOT NULL,
    telefono               VARCHAR(30),
    asunto                 VARCHAR(200) NOT NULL,
    descripcion            TEXT         NOT NULL,
    estado                 VARCHAR(20)  NOT NULL DEFAULT 'RECIBIDO',
    respuesta              TEXT,
    respondido_por         VARCHAR(150),
    fecha_creacion         TIMESTAMP    NOT NULL DEFAULT now(),
    fecha_limite_respuesta TIMESTAMP    NOT NULL,
    fecha_respuesta        TIMESTAMP,
    CONSTRAINT ck_pqrs_tipo CHECK (tipo IN ('PETICION', 'QUEJA', 'RECLAMO', 'SUGERENCIA')),
    CONSTRAINT ck_pqrs_estado CHECK (estado IN ('RECIBIDO', 'EN_TRAMITE', 'RESUELTO', 'CERRADO'))
);

CREATE INDEX ix_pqrs_estado ON pqrs (estado);
CREATE INDEX ix_pqrs_radicado ON pqrs (radicado);
