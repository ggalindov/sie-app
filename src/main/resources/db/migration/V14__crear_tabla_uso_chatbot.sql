-- Uso real (tokens y costo) del chatbot por mes calendario. Existe para poder
-- frenar el consumo de la API de Anthropic ANTES de que supere el presupuesto
-- mensual configurado (ver ChatbotService), en vez de solo limitar por número
-- de conversaciones (que no tiene relación directa con el costo real).
--
-- El costo se guarda en "micro-USD" (1 = 0.000001 USD), no en centavos: con
-- las tarifas de Claude Haiku (USD 1 / millón de tokens de entrada, USD 5 /
-- millón de salida) el costo de una sola llamada en centavos redondearía a 0
-- casi siempre. En micro-USD, 1 token de entrada = 1 micro-USD exacto y 1
-- token de salida = 5 micro-USD exactos: aritmética entera sin pérdida.
CREATE TABLE chatbot_uso_mensual (
    anio_mes VARCHAR(7) PRIMARY KEY, -- 'YYYY-MM'
    tokens_entrada BIGINT NOT NULL DEFAULT 0,
    tokens_salida BIGINT NOT NULL DEFAULT 0,
    costo_micro_usd BIGINT NOT NULL DEFAULT 0,
    actualizado_en TIMESTAMP NOT NULL DEFAULT now()
);

-- Suma atómica de uso real tras cada llamada exitosa al modelo. El incremento
-- vive en la base de datos (INSERT ... ON CONFLICT) para que dos peticiones
-- concurrentes no se pisen entre sí como pasaría con un contador en memoria.
CREATE OR REPLACE FUNCTION fn_registrar_uso_chatbot(
    p_tokens_entrada BIGINT,
    p_tokens_salida BIGINT,
    p_costo_micro_usd BIGINT
)
    RETURNS BIGINT
    LANGUAGE sql
AS
$$
INSERT INTO chatbot_uso_mensual (anio_mes, tokens_entrada, tokens_salida, costo_micro_usd, actualizado_en)
VALUES (to_char(now(), 'YYYY-MM'), p_tokens_entrada, p_tokens_salida, p_costo_micro_usd, now())
ON CONFLICT (anio_mes) DO UPDATE SET
    tokens_entrada = chatbot_uso_mensual.tokens_entrada + excluded.tokens_entrada,
    tokens_salida = chatbot_uso_mensual.tokens_salida + excluded.tokens_salida,
    costo_micro_usd = chatbot_uso_mensual.costo_micro_usd + excluded.costo_micro_usd,
    actualizado_en = now()
RETURNING costo_micro_usd;
$$;

-- Costo acumulado (en micro-USD) del mes calendario actual. 0 si todavía no
-- hubo ninguna llamada este mes (no existe fila).
CREATE OR REPLACE FUNCTION fn_costo_mensual_chatbot()
    RETURNS BIGINT
    LANGUAGE sql
AS
$$
SELECT COALESCE(
    (SELECT costo_micro_usd FROM chatbot_uso_mensual WHERE anio_mes = to_char(now(), 'YYYY-MM')),
    0
);
$$;
