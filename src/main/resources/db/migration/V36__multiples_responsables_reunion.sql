-- Reemplaza el responsable único de una reunión (abogado_asignado_id) por una relación N:M --
-- pedido explícito del usuario: "tanto admin como abogado pueda vincular a 1 o mas
-- responsables a la llamada, quiere decir mas abogados" (ver Solicitud.java / SolicitudService).
CREATE TABLE solicitud_responsables (
    solicitud_id BIGINT NOT NULL REFERENCES solicitudes (id) ON DELETE CASCADE,
    usuario_interno_id BIGINT NOT NULL REFERENCES usuarios_internos (id),
    PRIMARY KEY (solicitud_id, usuario_interno_id)
);

-- Conserva el responsable ya asignado a cada reunión existente antes de borrar la columna.
INSERT INTO solicitud_responsables (solicitud_id, usuario_interno_id)
SELECT id, abogado_asignado_id FROM solicitudes WHERE abogado_asignado_id IS NOT NULL;

DROP INDEX IF EXISTS ix_solicitudes_calendario;
ALTER TABLE solicitudes DROP COLUMN abogado_asignado_id;
CREATE INDEX ix_solicitudes_calendario ON solicitudes (fecha_cita);
CREATE INDEX ix_solicitud_responsables_usuario ON solicitud_responsables (usuario_interno_id);
