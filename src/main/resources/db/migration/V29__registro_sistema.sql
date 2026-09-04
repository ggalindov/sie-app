-- Bitácora de procesos del sistema visible en el panel (/admin/registro), ver
-- registro.RegistroSistema. Nunca guarda datos sensibles de clientes, solo el tipo de
-- proceso, una descripción/resumen, si tuvo éxito, y cuándo.
CREATE TABLE registro_sistema (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tipo        VARCHAR(40) NOT NULL,
    descripcion TEXT NOT NULL,
    detalle     TEXT,
    exitoso     BOOLEAN NOT NULL,
    fecha_hora  TIMESTAMP NOT NULL DEFAULT now()
);

-- El panel siempre lista ordenado por fecha descendente, a veces filtrado por tipo.
CREATE INDEX ix_registro_sistema_fecha_hora ON registro_sistema (fecha_hora DESC);
CREATE INDEX ix_registro_sistema_tipo_fecha ON registro_sistema (tipo, fecha_hora DESC);
