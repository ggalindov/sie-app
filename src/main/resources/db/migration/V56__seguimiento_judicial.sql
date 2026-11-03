-- Módulo de seguimiento judicial 24/7 (ver sie.siejuridicos.vigilancia): guarda un historial
-- PROPIO de las actuaciones reales de la Rama Judicial (CPNU) para cada caso, en vez de
-- consultarlas en vivo cada vez -- así se puede detectar qué actuación es NUEVA desde la
-- última vez que se revisó (fecha_detectada), no solo mostrar el estado actual.

-- Cachea el idProceso interno de la Rama Judicial una vez resuelto por radicado (ver
-- RamaJudicialCpnuClient.buscarIdProceso()), para no tener que volver a resolverlo en cada
-- ciclo de sincronización -- una llamada menos por caso en cada corrida. Null: todavía no se
-- ha podido resolver (radicado no es CPNU válido, o aún no se ha corrido el seguimiento).
ALTER TABLE casos
    ADD COLUMN id_proceso_cpnu BIGINT;

CREATE TABLE actuaciones_judiciales (
    id BIGSERIAL PRIMARY KEY,
    id_caso BIGINT NOT NULL REFERENCES casos(id) ON DELETE CASCADE,
    -- idRegActuacion de la propia Rama Judicial: llave real de deduplicación, no una
    -- combinación de fecha+nombre (dos actuaciones distintas del mismo día pueden compartir
    -- nombre, ej. dos "Recepción memorial" el mismo día).
    id_actuacion_externa BIGINT NOT NULL,
    fecha_actuacion DATE,
    nombre_actuacion VARCHAR(255) NOT NULL,
    anotacion TEXT,
    fecha_registro DATE,
    con_documentos BOOLEAN NOT NULL DEFAULT FALSE,
    -- Calculado una sola vez al guardar (ver ClasificadorActuacionFundamental), no en cada
    -- lectura: permite filtrar rápido "solo las actuaciones de fondo" sin tener que
    -- reevaluar la lista de palabras clave contra miles de filas ya guardadas.
    es_fundamental BOOLEAN NOT NULL DEFAULT FALSE,
    -- Cuándo NUESTRO sistema vio esta actuación por primera vez -- distinto de
    -- fecha_registro (cuándo el despacho la publicó). Es el dato real de "seguimiento": si
    -- fecha_detectada es de hoy, es una novedad de esta corrida.
    fecha_detectada TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_actuaciones_caso_externa UNIQUE (id_caso, id_actuacion_externa)
);

CREATE INDEX idx_actuaciones_judiciales_caso ON actuaciones_judiciales(id_caso);
CREATE INDEX idx_actuaciones_judiciales_fundamental ON actuaciones_judiciales(es_fundamental);
