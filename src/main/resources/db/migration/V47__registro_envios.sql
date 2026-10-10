-- Bitácora de cada correo y mensaje de WhatsApp que el sistema envía, con el destinatario --
-- pedido explícito del usuario: "pon tambien un registro de correos y whatsapp que salen del
-- sistema y para quien salen". A diferencia de registro_sistema (ver V29, que a propósito
-- NUNCA guarda datos de un cliente), esta tabla sí necesita guardar a quién se le escribió,
-- porque es justo el dato que se pidió poder ver -- por eso destinatario_nombre,
-- destinatario_contacto y resumen van cifrados (AES-256-GCM, ver CampoCifradoConverter),
-- igual que el resto de los datos de contacto reales del sistema (clientes, actividades del
-- CRM). Solo visible desde el panel para ADMIN_GENERAL (ver RegistroEnvioAdminController).
CREATE TABLE registro_envios (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    canal                 VARCHAR(20) NOT NULL,
    tipo                  VARCHAR(40) NOT NULL,
    destinatario_nombre   TEXT,
    destinatario_contacto TEXT NOT NULL,
    resumen               TEXT NOT NULL,
    exitoso               BOOLEAN NOT NULL,
    fecha_hora            TIMESTAMP NOT NULL DEFAULT now()
);

-- El panel siempre lista ordenado por fecha descendente, a veces filtrado por canal y/o tipo.
CREATE INDEX ix_registro_envios_fecha_hora ON registro_envios (fecha_hora DESC);
CREATE INDEX ix_registro_envios_canal_fecha ON registro_envios (canal, fecha_hora DESC);
CREATE INDEX ix_registro_envios_tipo_fecha ON registro_envios (tipo, fecha_hora DESC);
