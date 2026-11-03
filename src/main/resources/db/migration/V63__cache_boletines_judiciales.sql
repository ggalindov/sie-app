-- Pedido explícito del usuario: "que todos los boletines nuevos el sistema los guarde por
-- una semana en el sistema por caso y luego se borren" -- a diferencia de la decisión
-- anterior de NO custodiar PDFs de forma permanente (sigue en pie, nunca se vuelve un DMS),
-- esto es un cache TEMPORAL con vencimiento real: 7 días por boletín, nunca para siempre.
-- Sirve para adjuntar el PDF en los correos de digest (ver BoletinJudicialCacheService) sin
-- tener que volver a descargarlo cada vez, y para no enviarle el mismo boletín dos veces al
-- gerente/cliente en distintas corridas del digest (enviado_gerente/enviado_cliente).
CREATE TABLE boletines_judiciales_cache (
    id BIGSERIAL PRIMARY KEY,
    id_caso BIGINT NOT NULL REFERENCES casos(id) ON DELETE CASCADE,
    id_actuacion BIGINT NULL REFERENCES actuaciones_judiciales(id) ON DELETE SET NULL,
    nombre_archivo VARCHAR(255) NOT NULL,
    url_origen TEXT NOT NULL,
    contenido_bytes BYTEA NOT NULL,
    hash_sha256 VARCHAR(64) NOT NULL,
    enviado_gerente BOOLEAN NOT NULL DEFAULT FALSE,
    enviado_cliente BOOLEAN NOT NULL DEFAULT FALSE,
    fecha_descarga TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- Vencimiento real a 7 días -- BoletinJudicialCachePurgaScheduler borra todo lo que ya
    -- pasó esta fecha, sin excepción.
    fecha_expiracion TIMESTAMP NOT NULL,
    -- Mismo caso + misma URL de origen nunca se cachea dos veces (ver
    -- BoletinJudicialCacheService.cachearSiNuevo -- idempotente).
    CONSTRAINT uq_boletin_cache_caso_url UNIQUE (id_caso, url_origen)
);

CREATE INDEX idx_boletines_cache_caso ON boletines_judiciales_cache(id_caso);
CREATE INDEX idx_boletines_cache_expiracion ON boletines_judiciales_cache(fecha_expiracion);
CREATE INDEX idx_boletines_cache_pendiente_gerente ON boletines_judiciales_cache(enviado_gerente) WHERE NOT enviado_gerente;
CREATE INDEX idx_boletines_cache_pendiente_cliente ON boletines_judiciales_cache(id_caso, enviado_cliente) WHERE NOT enviado_cliente;
