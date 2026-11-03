-- Registro y control estricto de créditos de la API de Croma (usecroma.com)
-- Plan Free: $0 USD / mes, 1,500 créditos mensuales (150 consultas en vivo de 10 créditos cada una).
-- Este control impide matemáticamente superar la cuota gratuita para evitar cualquier cobro imprevisto.

CREATE TABLE IF NOT EXISTS croma_creditos_mensual (
    id                     BIGSERIAL PRIMARY KEY,
    mes_ano                VARCHAR(7) NOT NULL UNIQUE, -- Formato 'YYYY-MM', ej: '2026-11'
    creditos_consumidos    INT NOT NULL DEFAULT 0,
    consultas_realizadas   INT NOT NULL DEFAULT 0,
    limite_creditos        INT NOT NULL DEFAULT 1500,
    fecha_actualizacion    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_croma_creditos_mes ON croma_creditos_mensual(mes_ano);
