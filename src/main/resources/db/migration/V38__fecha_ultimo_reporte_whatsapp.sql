-- Desacopla la frecuencia del reporte de casos por canal (pedido explicito del usuario):
-- Correo: se envia semanalmente (cada lunes) a costo  por SMTP.
-- WhatsApp: se envia quincenalmente (dos veces al mes: dias 1 y 15) para reducir costos de Meta Cloud API.
ALTER TABLE casos ADD COLUMN fecha_ultimo_reporte_whatsapp TIMESTAMP;

