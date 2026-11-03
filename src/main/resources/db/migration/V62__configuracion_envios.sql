-- Pedido explícito del usuario: "implementa en el dashboard principal del administrador un
-- boton para apagar los envios de correo, apagar los envios de whatsapp (boton
-- independiente notificacones, boletin, marketing entre otros)" -- a diferencia de
-- app.bloqueo-total-clientes (una propiedad estática que solo cambia reiniciando el
-- backend, el interruptor MAESTRO de seguridad que sigue existiendo tal cual), esto es un
-- interruptor POR CATEGORÍA Y CANAL que el admin puede prender/apagar en caliente desde el
-- panel, sin reiniciar nada. clave es un valor de ConfiguracionEnvio.Clave (categoría +
-- canal), ver esa clase para la lista completa.
CREATE TABLE configuracion_envios (
    clave VARCHAR(50) PRIMARY KEY,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_modificacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO configuracion_envios (clave, activo) VALUES
    ('NOTIFICACIONES_CORREO', TRUE),
    ('NOTIFICACIONES_WHATSAPP', TRUE),
    ('REPORTE_PERIODICO_CORREO', FALSE),
    ('REPORTE_PERIODICO_WHATSAPP', FALSE),
    ('BOLETIN_JUDICIAL_CORREO', TRUE),
    ('BOLETIN_JUDICIAL_WHATSAPP', TRUE),
    ('MARKETING_CORREO', TRUE),
    ('MARKETING_WHATSAPP', TRUE);
