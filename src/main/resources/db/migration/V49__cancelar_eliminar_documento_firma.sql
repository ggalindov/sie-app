-- Pedido explícito del usuario: una forma de CANCELAR un proceso de firma en marcha desde
-- el panel (gerencia decide no seguir adelante, nadie rechazó firmar) y, una vez cancelado
-- o anulado, poder ELIMINARLO del sistema por completo (ver DocumentoFirmaService#cancelar
-- y DocumentoAdminService#eliminar). CANCELADO es un estado NUEVO y distinto de ANULADO a
-- propósito (pedido explícito del usuario: "no uses anulado sino otra condicion como
-- cancelado") -- ANULADO sigue significando exclusivamente "una parte rechazó firmar o el
-- enlace expiró", nunca una decisión manual de gerencia. La eliminación en sí no necesita
-- ningún cambio de esquema: solo borra filas existentes en el orden correcto (ver el
-- servicio), por eso esta migración solo amplía los dos CHECK constraints que antes no
-- contemplaban estos valores nuevos.

ALTER TABLE documentos_firma DROP CONSTRAINT documentos_firma_estado_check;
ALTER TABLE documentos_firma ADD CONSTRAINT documentos_firma_estado_check
    CHECK (estado_documento IN ('DILIGENCIADO', 'EMITIDO', 'FIRMADO_STAFF', 'NOTIFICADO',
                                 'EN_FIRMA_PARTES', 'SELLADO', 'REMITIDO', 'ENCRIPTADO', 'ANULADO', 'CANCELADO'));

ALTER TABLE documento_eventos DROP CONSTRAINT documento_eventos_tipo_check;
ALTER TABLE documento_eventos ADD CONSTRAINT documento_eventos_tipo_check
    CHECK (tipo_evento IN ('DILIGENCIADO', 'EMITIDO', 'FIRMADO_STAFF', 'NOTIFICADO',
                            'EN_FIRMA_PARTES_INICIADO', 'FIRMADO_POR_PARTE', 'FIRMA_RECHAZADA', 'SELLADO', 'REMITIDO', 'ENCRIPTADO',
                            'ALTERADO_DETECTADO', 'VERIFICACION_CONSULTADA',
                            'SOLICITUD_CAMBIO_CREADA', 'SOLICITUD_CAMBIO_EJECUTADA',
                            'LLAVE_APLICACION_ROTADA', 'SELLO_TIEMPO_APLICADO', 'SELLO_TIEMPO_FALLIDO',
                            'CERTIFICADO_EVIDENCIA_DESCARGADO',
                            'RECORDATORIO_ENVIADO', 'ENLACE_REVOCADO', 'PROCESO_EXPIRADO', 'PROCESO_ANULADO',
                            'PROCESO_CANCELADO'));
