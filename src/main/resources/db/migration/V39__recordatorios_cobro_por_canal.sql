-- Registra de forma independiente la notificacion de cobro por correo y por WhatsApp (pedido explicito del usuario):
-- Permite saber exactamente a quien se le envio correo y a quien WhatsApp en el panel de Cobros Pendientes.
ALTER TABLE clientes_cobro ADD COLUMN fecha_ultimo_recordatorio_correo TIMESTAMP;
ALTER TABLE clientes_cobro ADD COLUMN fecha_ultimo_recordatorio_whatsapp TIMESTAMP;

-- Inicializar con la fecha de ultimo recordatorio historica para correo (que si se envio con exito)
UPDATE clientes_cobro
SET fecha_ultimo_recordatorio_correo = fecha_ultimo_recordatorio
WHERE fecha_ultimo_recordatorio IS NOT NULL;
