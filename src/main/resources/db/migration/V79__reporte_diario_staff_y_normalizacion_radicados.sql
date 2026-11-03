-- V79: Notificación diaria de movimientos procesales al staff (gerencia y abogados)
-- y normalización masiva de radicados judiciales (23 dígitos).

-- 1. Normalización de radicados existentes con formato sucio o notas adjuntas (ej. '(2026-01059)')
UPDATE casos 
SET radicado_id = substring(regexp_replace(radicado_id, '[^0-9]', '', 'g') from 1 for 23)
WHERE radicado_id ~ '\d{23}' AND length(radicado_id) != 23;

-- 2. Trazabilidad de actuaciones notificadas al staff
ALTER TABLE actuaciones_judiciales 
ADD COLUMN IF NOT EXISTS notificada_staff BOOLEAN NOT NULL DEFAULT true;

ALTER TABLE actuaciones_judiciales 
ADD COLUMN IF NOT EXISTS fecha_notificada_staff TIMESTAMP WITHOUT TIME ZONE;

-- Las actuaciones históricas existentes quedan en true por defecto para no enviar cientos
-- de registros viejos acumulados. Las que se detecten en cada nuevo ciclo se guardarán con false.

CREATE INDEX IF NOT EXISTS idx_actuaciones_notificada_staff 
ON actuaciones_judiciales(notificada_staff) 
WHERE notificada_staff = false;
