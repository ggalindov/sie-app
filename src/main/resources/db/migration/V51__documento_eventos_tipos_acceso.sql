-- Hasta ahora el certificado de evidencia era el único "acceso" del propio firmante que
-- quedaba registrado en la línea de tiempo (CERTIFICADO_EVIDENCIA_DESCARGADO, declarado en
-- V48 pero nunca disparado por ningún código -- se activa en esta misma tanda de cambios).
-- Se agregan 2 tipos nuevos para que la descarga del documento final y la consulta del
-- documento a revisar (antes de firmar) también dejen rastro -- auditoría completa de
-- quién vio/descargó qué y cuándo, no solo de quién firmó.
ALTER TABLE documento_eventos DROP CONSTRAINT documento_eventos_tipo_check;
ALTER TABLE documento_eventos ADD CONSTRAINT documento_eventos_tipo_check
    CHECK (tipo_evento IN ('DILIGENCIADO', 'EMITIDO', 'FIRMADO_STAFF', 'NOTIFICADO',
                            'EN_FIRMA_PARTES_INICIADO', 'FIRMADO_POR_PARTE', 'FIRMA_RECHAZADA', 'SELLADO', 'REMITIDO', 'ENCRIPTADO',
                            'ALTERADO_DETECTADO', 'VERIFICACION_CONSULTADA',
                            'SOLICITUD_CAMBIO_CREADA', 'SOLICITUD_CAMBIO_EJECUTADA',
                            'LLAVE_APLICACION_ROTADA', 'SELLO_TIEMPO_APLICADO', 'SELLO_TIEMPO_FALLIDO',
                            'CERTIFICADO_EVIDENCIA_DESCARGADO', 'DOCUMENTO_FINAL_DESCARGADO', 'DOCUMENTO_REVISION_CONSULTADA',
                            'RECORDATORIO_ENVIADO', 'ENLACE_REVOCADO', 'PROCESO_EXPIRADO', 'PROCESO_ANULADO',
                            'PROCESO_CANCELADO'));
