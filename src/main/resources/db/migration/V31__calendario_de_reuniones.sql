-- Calendario visual de reuniones (ver SolicitudService.agendarCita): cada solicitud con
-- fecha_cita queda asociada a UN abogado responsable (abogado_asignado_id) y a un enlace de
-- acceso a la reunion (link_reunion). abogado_asignado_id es lo que permite que el
-- administrador vea las reuniones de todos los abogados mientras que cada abogado solo ve
-- las suyas -- sin esta columna no habria forma de saber de quien es cada cita.
ALTER TABLE solicitudes
    ADD COLUMN abogado_asignado_id BIGINT REFERENCES usuarios_internos (id),
    ADD COLUMN link_reunion         VARCHAR(500);

-- soporta la consulta del calendario: citas de un rango de fechas, opcionalmente filtradas
-- por abogado (rol ABOGADO). Parcial (WHERE fecha_cita IS NOT NULL) por el mismo motivo que
-- ix_solicitudes_fecha_cita en V11: la mayoria de solicitudes no tiene cita agendada.
CREATE INDEX ix_solicitudes_calendario ON solicitudes (fecha_cita, abogado_asignado_id)
    WHERE fecha_cita IS NOT NULL;
