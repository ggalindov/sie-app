-- Pedido explícito del usuario (2026-11-09): "implementa una funcionalidad para todos los
-- lunes de la semana a las 8 am que le mande a la gerencia un reporte de casos con fallos,
-- radicados no correctos o invalidos, correos y numeros de telefono fallidos o que
-- simplemente no han tenido reportes correctos en las ramas" -- ver
-- ReporteCalidadDatosCasosScheduler/CasoService.generarReporteCalidadDatos(). Arranca
-- ENCENDIDO (a diferencia de CORREO_DESPACHO_JUDICIAL/REPORTE_PERIODICO en V62/V82): es un
-- reporte puramente INTERNO hacia gerencia, nunca correspondencia saliente a un cliente o
-- despacho, así que no hay riesgo de enviar algo no revisado a un tercero.
INSERT INTO configuracion_envios (clave, activo) VALUES ('REPORTE_CALIDAD_DATOS_CORREO', TRUE);
