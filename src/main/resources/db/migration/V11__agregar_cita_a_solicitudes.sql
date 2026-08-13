-- El abogado/admin agenda la cita manualmente sobre una solicitud existente (no hay
-- calendario público de autoagendamiento). recordatorio_enviado evita que el job diario
-- de recordatorios reenvíe el correo si corre más de una vez el mismo día.
ALTER TABLE solicitudes
    ADD COLUMN fecha_cita         TIMESTAMP,
    ADD COLUMN recordatorio_enviado BOOLEAN NOT NULL DEFAULT FALSE;

-- soporta la consulta diaria "citas de hoy sin recordatorio enviado"
CREATE INDEX ix_solicitudes_fecha_cita ON solicitudes (fecha_cita) WHERE fecha_cita IS NOT NULL;
