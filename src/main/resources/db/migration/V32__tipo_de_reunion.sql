-- Una reunion agendada ahora puede ser VIRTUAL (link_reunion, ya agregado en V31) o
-- PRESENCIAL (lugar_reunion, nueva columna) -- ver Solicitud.tipoReunion y
-- SolicitudService.agendarCita, que exige el campo correspondiente segun el tipo y limpia
-- el otro. Default 'VIRTUAL' para las filas ya existentes (todas las reuniones agendadas
-- hasta ahora fueron virtuales).
ALTER TABLE solicitudes
    ADD COLUMN tipo_reunion  VARCHAR(20) NOT NULL DEFAULT 'VIRTUAL',
    ADD COLUMN lugar_reunion VARCHAR(300),
    ADD CONSTRAINT ck_solicitudes_tipo_reunion CHECK (tipo_reunion IN ('VIRTUAL', 'PRESENCIAL'));
