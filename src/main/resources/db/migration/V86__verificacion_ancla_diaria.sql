-- Cierra el único hueco real encontrado en la auditoría del motor de firma electrónica
-- (pedido explícito del usuario: "cualquier cambio sea automaticamente detectado... de
-- ninguna forma se pueda vulnerar"): el ancla diaria (anclas_diarias_integridad) se
-- generaba y sellaba con RFC 3161 todos los días, pero nunca se volvía a comparar contra
-- el estado actual de documento_eventos_checkpoints. Un atacante con acceso de escritura
-- directo a la base de datos podía alterar tanto documento_eventos como sus checkpoints
-- (defeando la detección de truncamiento de DocumentoEventoChainVerifier) sin que nada lo
-- recalculara jamás contra el sello externo ya hecho. Ver AnclaDiariaIntegridadScheduler.
ALTER TABLE anclas_diarias_integridad
    ADD COLUMN integra BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN detalle_compromiso TEXT,
    ADD COLUMN verificado_en TIMESTAMP;
