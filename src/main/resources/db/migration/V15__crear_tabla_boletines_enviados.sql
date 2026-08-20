-- Historial de boletines mensuales enviados desde el panel admin (resumen de cambios
-- normativos). No hay concepto de "borrador": el abogado/admin escribe y envía en el
-- mismo paso (ver BoletinService), esta tabla solo deja constancia de lo ya enviado.
CREATE TABLE boletines_enviados
(
    id                    BIGSERIAL PRIMARY KEY,
    asunto                VARCHAR(200) NOT NULL,
    cuerpo                TEXT         NOT NULL,
    cantidad_destinatarios INTEGER     NOT NULL,
    id_usuario_envio      BIGINT       NOT NULL REFERENCES usuarios_internos (id),
    fecha_envio           TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX ix_boletines_enviados_fecha ON boletines_enviados (fecha_envio DESC);
