-- Notificación del radicado también por WhatsApp (línea de atención de la firma, ver
-- WhatsAppService), además del correo. whatsapp_enviado es independiente de correo_enviado
-- a propósito: no todos los clientes tienen teléfono capturado en la hoja, y WhatsApp puede
-- fallar (número inválido, plantilla no aprobada aún) sin que eso afecte el correo, que sigue
-- su propio camino.
ALTER TABLE casos
    ADD COLUMN whatsapp_enviado BOOLEAN NOT NULL DEFAULT false;

-- Los casos que ya tenían el correo enviado antes de esta migración no tenían ninguna
-- integración de WhatsApp todavía: no hay nada que "ya se envió" de verdad, se quedan en
-- false para que, cuando se configure WhatsApp, el botón de notificaciones pendientes los
-- recoja a todos de una vez (mismo criterio que correo_enviado en la migración V24, pero al
-- revés: ahí sí se marcaban como ya enviados porque el correo sí existía desde el principio).
