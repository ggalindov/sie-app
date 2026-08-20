-- FAQ auto-alimentada: cada pregunta que le llega al chatbot se normaliza (minúsculas,
-- sin tildes/puntuación) y se cuenta. pregunta_normalizada es UNIQUE para poder hacer
-- upsert atómico (ON CONFLICT ... DO UPDATE, ver PreguntaFrecuenteRepository) e
-- incrementar el conteo sin condición de carrera cuando dos usuarios preguntan lo mismo
-- casi al mismo tiempo. Solo se muestra como "candidata" en el panel cuando conteo supera
-- el umbral configurado (app.faq.umbral-candidata); por debajo de eso la fila existe pero
-- no aparece en la cola de moderación (ver PreguntaFrecuenteService.listarCandidatas).
CREATE TABLE preguntas_frecuentes
(
    id                    BIGSERIAL PRIMARY KEY,
    pregunta_normalizada  VARCHAR(500) NOT NULL,
    pregunta_ejemplo      VARCHAR(500) NOT NULL,
    respuesta_sugerida    TEXT,
    respuesta_final       TEXT,
    conteo                INTEGER      NOT NULL DEFAULT 1,
    estado                VARCHAR(20)  NOT NULL DEFAULT 'CANDIDATA',
    fecha_primera_vez     TIMESTAMP    NOT NULL DEFAULT now(),
    fecha_actualizacion   TIMESTAMP    NOT NULL DEFAULT now(),
    fecha_moderacion      TIMESTAMP,
    CONSTRAINT uq_preguntas_frecuentes_normalizada UNIQUE (pregunta_normalizada),
    CONSTRAINT ck_preguntas_frecuentes_estado CHECK (estado IN ('CANDIDATA', 'APROBADA', 'RECHAZADA'))
);

CREATE INDEX ix_preguntas_frecuentes_estado_conteo ON preguntas_frecuentes (estado, conteo DESC);
