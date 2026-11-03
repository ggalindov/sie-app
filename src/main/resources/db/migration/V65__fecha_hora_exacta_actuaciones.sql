-- Pedido explícito del usuario: "necesito que el sistema traiga todos los sesguimintos,
-- vigilancia y reportes completos y minusiosos... el reporte completo por fecha y hora
-- exacta". El CPNU de la Rama Judicial SÍ devuelve la hora exacta de fecha_actuacion y
-- fecha_registro, pero RamaJudicialCpnuClient la venía descartando con un .toLocalDate()
-- (ver el comentario de ActuacionJudicial.java) -- esto amplía las columnas de DATE a
-- TIMESTAMP para poder guardarla de verdad de aquí en adelante. Las filas ya existentes se
-- quedan con hora 00:00:00 (Postgres las convierte así automáticamente): no hay forma de
-- recuperar una hora que nunca se guardó, pero toda actuación nueva desde ahora sí la trae.
ALTER TABLE actuaciones_judiciales
    ALTER COLUMN fecha_actuacion TYPE TIMESTAMP,
    ALTER COLUMN fecha_registro TYPE TIMESTAMP;
