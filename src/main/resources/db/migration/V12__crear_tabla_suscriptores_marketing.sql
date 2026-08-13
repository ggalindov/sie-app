-- Lista de correos que aceptaron explícitamente recibir marketing (checkbox separado
-- del aviso de tratamiento de datos del formulario de contacto, ver CrearSolicitudRequest).
CREATE TABLE suscriptores_marketing
(
    id                BIGSERIAL PRIMARY KEY,
    nombre            VARCHAR(150) NOT NULL,
    correo            VARCHAR(150) NOT NULL,
    fecha_suscripcion TIMESTAMP    NOT NULL DEFAULT now(),
    activo            BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_suscriptores_marketing_correo UNIQUE (correo)
);

CREATE INDEX ix_suscriptores_marketing_activo ON suscriptores_marketing (activo);
