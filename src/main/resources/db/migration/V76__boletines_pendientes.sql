-- Cola persistente de boletines que el ciclo no pudo completar (2026-11-01). Hallazgo real
-- del ciclo de esa noche: 68 descargas de PDF fallaron (67 en una sola hora, conexion
-- abortada) y esos documentos, ya localizados, se perdian sin rastro: el cursor avanzaba y
-- no se volvian a revisar hasta la siguiente vuelta completa. Ahora cada fallo queda aqui y
-- se reintenta al terminar el recorrido y en los ciclos siguientes, hasta que se descargue.
--   DESCARGA : el PDF ya se localizo (url_origen conocida) pero no se pudo bajar.
--   BUSQUEDA : la consulta al portal quedo incompleta (timeout, listado o verificacion
--              fallida), asi que no se sabe si habia boletin: se repite solo esa actuacion.
CREATE TABLE boletines_pendientes (
    id                     BIGSERIAL PRIMARY KEY,
    id_caso                BIGINT NOT NULL REFERENCES casos(id) ON DELETE CASCADE,
    id_actuacion           BIGINT REFERENCES actuaciones_judiciales(id) ON DELETE SET NULL,
    tipo                   VARCHAR(10) NOT NULL CHECK (tipo IN ('DESCARGA', 'BUSQUEDA')),
    url_origen             TEXT,
    nombre_archivo         TEXT,
    coincide_nombre        BOOLEAN NOT NULL DEFAULT FALSE,
    intentos               INT NOT NULL DEFAULT 0,
    agotado                BOOLEAN NOT NULL DEFAULT FALSE,
    ultimo_error           TEXT,
    fecha_creacion         TIMESTAMP NOT NULL DEFAULT now(),
    fecha_ultimo_intento   TIMESTAMP
);

CREATE UNIQUE INDEX uq_boletines_pendientes_descarga
    ON boletines_pendientes (id_caso, url_origen) WHERE tipo = 'DESCARGA';
CREATE UNIQUE INDEX uq_boletines_pendientes_busqueda
    ON boletines_pendientes (id_actuacion) WHERE tipo = 'BUSQUEDA';
CREATE INDEX idx_boletines_pendientes_activos
    ON boletines_pendientes (agotado, tipo, fecha_creacion);
