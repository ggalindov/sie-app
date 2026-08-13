CREATE TABLE conversaciones_chatbot
(
    id                 BIGSERIAL PRIMARY KEY,
    id_solicitud       BIGINT,
    fecha              TIMESTAMP    NOT NULL DEFAULT now(),
    cantidad_mensajes  INTEGER      NOT NULL DEFAULT 0,
    resumen            VARCHAR(500),
    CONSTRAINT fk_conversaciones_solicitud FOREIGN KEY (id_solicitud) REFERENCES solicitudes (id) ON DELETE SET NULL
);

CREATE INDEX ix_conversaciones_fecha ON conversaciones_chatbot (fecha);
CREATE INDEX ix_conversaciones_solicitud ON conversaciones_chatbot (id_solicitud);
