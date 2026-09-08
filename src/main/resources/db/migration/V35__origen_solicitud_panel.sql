-- Agrega 'PANEL' a los valores permitidos de origen (ver OrigenSolicitud.java /
-- SolicitudService.crearDirecta): una reunión agendada directamente desde el panel para un
-- caso ya existente o un cliente fuera del sistema, sin pasar por el formulario público, el
-- chatbot ni WhatsApp.
ALTER TABLE solicitudes DROP CONSTRAINT ck_solicitudes_origen;
ALTER TABLE solicitudes ADD CONSTRAINT ck_solicitudes_origen
    CHECK (origen IN ('FORMULARIO', 'CHATBOT', 'WHATSAPP', 'PANEL'));
