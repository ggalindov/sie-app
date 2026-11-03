-- Gap real detectado en la auditoría del plan de desarrollo: el término procesal solo se
-- veía como una insignia dentro del modal de Seguimiento -- si el abogado no entraba a
-- revisarlo, no había ninguna alerta activa en otro lado del panel. Esta tabla persiste
-- CADA término detectado (oficial o inferido, ver InferenciaTerminoProcesal) y lo liga a la
-- tarea real que se le crea al abogado asignado del caso (ver TerminoProcesalService),
-- para que se pueda ver con semáforo en /admin/calendario y como tarea en /admin/tareas.
--
-- "estado" (CORRIENDO/POR_VENCER/VENCIDO) NO se persiste aquí a propósito: depende de la
-- fecha de HOY, así que se recalcula al vuelo cada vez que se lee (ver
-- InferenciaTerminoProcesal.calcularEstado), nunca se guarda un valor que quedaría
-- desactualizado al día siguiente.
CREATE TABLE terminos_procesales (
    id BIGSERIAL PRIMARY KEY,
    id_caso BIGINT NOT NULL REFERENCES casos(id) ON DELETE CASCADE,
    -- Único por actuación: una actuación abre a lo sumo un término en este modelo, y nunca
    -- se vuelve a procesar una vez ya tiene su fila aquí (ver
    -- TerminoProcesalRepository.existsByActuacionId).
    id_actuacion BIGINT NOT NULL UNIQUE REFERENCES actuaciones_judiciales(id) ON DELETE CASCADE,
    -- Null si no se pudo crear la tarea (el caso no tenía abogado asignado en ese momento) --
    -- el término igual queda registrado, simplemente sin la alerta activa todavía.
    id_tarea BIGINT NULL REFERENCES tareas(id) ON DELETE SET NULL,
    descripcion VARCHAR(255) NOT NULL,
    dias_habiles INT NOT NULL,
    fecha_vencimiento DATE NOT NULL,
    -- true: fechaInicial/fechaFinal reales informados por la Rama Judicial (ver migración
    -- V58). false: estimado por palabras clave (ver InferenciaTerminoProcesal.inferir).
    es_oficial BOOLEAN NOT NULL,
    fecha_creacion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_terminos_procesales_caso ON terminos_procesales(id_caso);
CREATE INDEX idx_terminos_procesales_vencimiento ON terminos_procesales(fecha_vencimiento);
