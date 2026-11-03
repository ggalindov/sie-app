-- Memoria de 7 días para informes profundos de Legal-Pro (Croma API)
-- Guarda el informe extraído de Croma por caso/radicado durante 7 días.
-- Si el abogado vuelve a abrir el caso dentro de los 7 días, carga de inmediato desde memoria sin gastar créditos.
-- Si el abogado presiona "Actualizar Informe", se sobrescribe y reinicia el contador de 7 días.
-- Al cumplirse el día 7, el informe se borra automáticamente.

CREATE TABLE IF NOT EXISTS croma_legal_pro_cache (
    id                  BIGSERIAL PRIMARY KEY,
    caso_id             BIGINT NULL REFERENCES casos(id) ON DELETE CASCADE,
    radicado            VARCHAR(50) NOT NULL UNIQUE,
    payload_json        TEXT NOT NULL,
    fecha_extraccion    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    fecha_expiracion    TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_croma_legal_pro_cache_caso ON croma_legal_pro_cache(caso_id);
CREATE INDEX IF NOT EXISTS idx_croma_legal_pro_cache_exp ON croma_legal_pro_cache(fecha_expiracion);
