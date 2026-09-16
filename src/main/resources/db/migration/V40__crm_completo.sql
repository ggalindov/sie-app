-- ==============================================================================
-- Migración V40: Módulo CRM Jurídico Integral
-- Unifica el ciclo de vida del cliente: Pipeline de Oportunidades (embudo Kanban),
-- Directorio 360° de Clientes, Bitácora de Interacciones y Tareas de Seguimiento.
-- ==============================================================================

-- 1. Tabla Central: crm_clientes
CREATE TABLE IF NOT EXISTS crm_clientes (
    id BIGSERIAL PRIMARY KEY,
    tipo VARCHAR(20) NOT NULL DEFAULT 'PERSONA_NATURAL', -- PERSONA_NATURAL | EMPRESA
    nombre TEXT NOT NULL,
    cedula_nit TEXT,
    cedula_nit_hash VARCHAR(64),
    correo TEXT,
    correo_hash VARCHAR(64),
    telefono TEXT,
    telefono_hash VARCHAR(64),
    direccion TEXT,
    ciudad VARCHAR(100),
    estado VARCHAR(30) NOT NULL DEFAULT 'ACTIVO', -- PROSPECTO | ACTIVO | INACTIVO | FINALIZADO
    etiqueta VARCHAR(50),                         -- VIP | LITIGIO | CONSULTORIA | RECURRENTE | etc.
    notas TEXT,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT NOW(),
    fecha_actualizacion TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_crm_clientes_correo_hash ON crm_clientes(correo_hash);
CREATE INDEX IF NOT EXISTS idx_crm_clientes_telefono_hash ON crm_clientes(telefono_hash);
CREATE INDEX IF NOT EXISTS idx_crm_clientes_cedula_hash ON crm_clientes(cedula_nit_hash);
CREATE INDEX IF NOT EXISTS idx_crm_clientes_estado ON crm_clientes(estado);

-- 2. Modificaciones en Solicitudes (Pipeline de Leads/Oportunidades)
ALTER TABLE solicitudes ADD COLUMN IF NOT EXISTS etapa_pipeline VARCHAR(30) NOT NULL DEFAULT 'NUEVO';
ALTER TABLE solicitudes ADD COLUMN IF NOT EXISTS valor_estimado NUMERIC(14,2);
ALTER TABLE solicitudes ADD COLUMN IF NOT EXISTS area_practica VARCHAR(100);
ALTER TABLE solicitudes ADD COLUMN IF NOT EXISTS cliente_crm_id BIGINT REFERENCES crm_clientes(id) ON DELETE SET NULL;

-- Sincronizar estado histórico de solicitudes hacia etapa_pipeline
UPDATE solicitudes
SET etapa_pipeline = CASE
    WHEN estado = 'CONTACTADO' THEN 'CONTACTADO'
    WHEN estado = 'CERRADO' THEN 'CONTRATADO'
    ELSE 'NUEVO'
END
WHERE etapa_pipeline = 'NUEVO';

-- 3. Vinculación en Clientes de Casos y Clientes de Cobros
ALTER TABLE clientes ADD COLUMN IF NOT EXISTS cliente_crm_id BIGINT REFERENCES crm_clientes(id) ON DELETE SET NULL;
ALTER TABLE clientes_cobro ADD COLUMN IF NOT EXISTS cliente_crm_id BIGINT REFERENCES crm_clientes(id) ON DELETE SET NULL;

-- 4. Tabla: crm_actividades (Timeline / Bitácora de Interacciones)
CREATE TABLE IF NOT EXISTS crm_actividades (
    id BIGSERIAL PRIMARY KEY,
    cliente_crm_id BIGINT NOT NULL REFERENCES crm_clientes(id) ON DELETE CASCADE,
    solicitud_id BIGINT REFERENCES solicitudes(id) ON DELETE SET NULL,
    caso_id BIGINT REFERENCES casos(id) ON DELETE SET NULL,
    tipo VARCHAR(30) NOT NULL, -- LLAMADA | WHATSAPP | CORREO | REUNION | NOTA_INTERNA | CAMBIO_ESTADO | PAGO
    titulo VARCHAR(255) NOT NULL,
    descripcion TEXT,
    usuario_nombre VARCHAR(150),
    fecha_actividad TIMESTAMP NOT NULL DEFAULT NOW(),
    fecha_creacion TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_crm_actividades_cliente ON crm_actividades(cliente_crm_id);
CREATE INDEX IF NOT EXISTS idx_crm_actividades_fecha ON crm_actividades(fecha_actividad DESC);

-- 5. Tabla: crm_tareas (Seguimiento y Pendientes)
CREATE TABLE IF NOT EXISTS crm_tareas (
    id BIGSERIAL PRIMARY KEY,
    cliente_crm_id BIGINT NOT NULL REFERENCES crm_clientes(id) ON DELETE CASCADE,
    titulo VARCHAR(255) NOT NULL,
    descripcion TEXT,
    fecha_vencimiento TIMESTAMP,
    completada BOOLEAN NOT NULL DEFAULT FALSE,
    prioridad VARCHAR(20) NOT NULL DEFAULT 'MEDIA', -- ALTA | MEDIA | BAJA
    usuario_nombre VARCHAR(150),
    fecha_creacion TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_crm_tareas_cliente ON crm_tareas(cliente_crm_id);
CREATE INDEX IF NOT EXISTS idx_crm_tareas_completada ON crm_tareas(completada);

-- 6. Población Inicial Segura: Migrar clientes existentes hacia crm_clientes
-- Migrar primero desde clientes_cobro (que tiene cédula/NIT y tipo de cliente)
INSERT INTO crm_clientes (tipo, nombre, cedula_nit, correo, telefono, telefono_hash, estado, notas, fecha_creacion, fecha_actualizacion)
SELECT
    CASE WHEN cc.tipo = 'EMPRESA' THEN 'EMPRESA' ELSE 'PERSONA_NATURAL' END,
    cc.nombre,
    cc.cedula_nit,
    cc.correo,
    cc.telefono,
    cc.telefono_hash,
    'ACTIVO',
    'Cliente importado desde Cobros Pendientes (fila ' || cc.numero_fila || ')',
    COALESCE(cc.fecha_creacion, NOW()),
    NOW()
FROM clientes_cobro cc
WHERE cc.activo = true;

-- Vincular clientes_cobro con los registros recién creados en crm_clientes
UPDATE clientes_cobro cc
SET cliente_crm_id = c.id
FROM crm_clientes c
WHERE cc.telefono_hash IS NOT NULL AND cc.telefono_hash = c.telefono_hash;

-- Migrar clientes de casos que no estén todavía en crm_clientes
INSERT INTO crm_clientes (tipo, nombre, correo, correo_hash, telefono, estado, notas, fecha_creacion, fecha_actualizacion)
SELECT
    'PERSONA_NATURAL',
    cl.nombre,
    cl.correo,
    cl.correo_hash,
    cl.telefono,
    'ACTIVO',
    'Cliente importado desde Expedientes Judiciales',
    COALESCE(cl.fecha_creacion, NOW()),
    NOW()
FROM clientes cl
WHERE cl.correo_hash IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM crm_clientes c WHERE c.correo_hash = cl.correo_hash
  );

-- Vincular clientes de casos
UPDATE clientes cl
SET cliente_crm_id = c.id
FROM crm_clientes c
WHERE cl.correo_hash IS NOT NULL AND cl.correo_hash = c.correo_hash;
