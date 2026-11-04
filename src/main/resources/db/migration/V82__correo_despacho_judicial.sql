-- Pedido explícito del usuario (2026-11-04): "termina toda la configuracion para que caso
-- que detecte que no tenga auto/boletin retorne peticion al buzon del juzgado, revisa como
-- lo hace lupa para hacerlo igual" -- LUPA, al no encontrar el documento oficial de una
-- actuacion por ningun canal automatico, le escribe directamente al correo oficial del
-- despacho (su propio buzon @cendoj.ramajudicial.gov.co) invocando el articulo 123 del CGP
-- y la Ley 1712 de 2014, con un documento de presentacion/autorizacion adjunto. Ver
-- CorreoDespachoJudicialService, unico escritor de estas dos columnas nuevas.

-- Nunca se le vuelve a pedir el mismo documento dos veces al mismo despacho para la MISMA
-- actuacion (idempotencia real, no solo una ventana de tiempo).
ALTER TABLE actuaciones_judiciales ADD COLUMN correo_despacho_solicitado_en TIMESTAMP;

-- Cache del correo oficial del despacho YA resuelto para este caso (via Croma Legal-Pro,
-- API de pago por credito) -- se reutiliza para TODAS las actuaciones futuras de ESE MISMO
-- caso sin volver a gastar un credito, ver CorreoDespachoJudicialService.resolverCorreoDespacho().
ALTER TABLE casos ADD COLUMN correo_oficial_despacho VARCHAR(255);
ALTER TABLE casos ADD COLUMN correo_oficial_despacho_fecha TIMESTAMP;

-- Arranca APAGADO a propósito (mismo patron que REPORTE_PERIODICO_CORREO en V62): esto
-- envia correspondencia real, en nombre de la firma, a despachos judiciales reales
-- invocando autoridad legal (Art. 123 CGP) -- debe ser el usuario quien lo prenda a
-- conciencia desde el panel principal tras revisarlo, nunca algo que arranque solo al
-- desplegar esta migracion.
INSERT INTO configuracion_envios (clave, activo) VALUES ('CORREO_DESPACHO_JUDICIAL', FALSE);
