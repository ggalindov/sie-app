CREATE TABLE solicitudes
(
    id                          BIGSERIAL PRIMARY KEY,
    nombre                      VARCHAR(150) NOT NULL,
    correo                      VARCHAR(150) NOT NULL,
    telefono                    VARCHAR(30),
    mensaje                     TEXT         NOT NULL,
    origen                      VARCHAR(20)  NOT NULL,
    estado                      VARCHAR(20)  NOT NULL DEFAULT 'NUEVO',
    notas_internas              TEXT,
    fecha_creacion              TIMESTAMP    NOT NULL DEFAULT now(),
    fecha_actualizacion_estado  TIMESTAMP,
    CONSTRAINT ck_solicitudes_origen CHECK (origen IN ('FORMULARIO', 'CHATBOT', 'WHATSAPP')),
    CONSTRAINT ck_solicitudes_estado CHECK (estado IN ('NUEVO', 'CONTACTADO', 'CERRADO'))
);

CREATE INDEX ix_solicitudes_correo ON solicitudes (correo);
CREATE INDEX ix_solicitudes_estado ON solicitudes (estado);
CREATE INDEX ix_solicitudes_fecha_creacion ON solicitudes (fecha_creacion);

-- soporta la verificación de duplicados de fn_crear_solicitud (correo + últimas 24h)
CREATE INDEX ix_solicitudes_correo_fecha ON solicitudes (correo, fecha_creacion);
