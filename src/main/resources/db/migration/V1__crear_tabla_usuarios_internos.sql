CREATE TABLE usuarios_internos
(
    id             BIGSERIAL PRIMARY KEY,
    nombre         VARCHAR(150) NOT NULL,
    correo         VARCHAR(150) NOT NULL,
    contrasena     VARCHAR(255) NOT NULL,
    rol            VARCHAR(20)  NOT NULL,
    fecha_creacion TIMESTAMP    NOT NULL DEFAULT now(),
    activo         BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_usuarios_internos_correo UNIQUE (correo),
    CONSTRAINT ck_usuarios_internos_rol CHECK (rol IN ('ADMIN_GENERAL', 'ABOGADO'))
);

CREATE INDEX ix_usuarios_internos_rol ON usuarios_internos (rol);
CREATE INDEX ix_usuarios_internos_activo ON usuarios_internos (activo);
