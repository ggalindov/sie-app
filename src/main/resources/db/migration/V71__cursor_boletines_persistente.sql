-- Bug real confirmado en vivo (2026-11-01): el cursor rotativo de BoletinJudicialCacheService
-- (qué actuación revisar primero en la próxima corrida, para no repetir siempre las mismas
-- cuando Publicaciones Procesales corta el lote) vivía solo en memoria (AtomicInteger). Un
-- reinicio del backend (frecuente en desarrollo, y cualquier despliegue en producción) lo
-- resetea a 0, perdiendo el avance acumulado -- si además el circuit breaker corta temprano
-- varias noches seguidas, los mismos primeros casos de la lista nunca dejan que el resto
-- reciba su turno. Una sola fila, nunca se borra ni se inserta una segunda.
CREATE TABLE vigilancia_cursor_boletines (
    id SMALLINT PRIMARY KEY,
    posicion INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT vigilancia_cursor_boletines_una_fila CHECK (id = 1)
);

INSERT INTO vigilancia_cursor_boletines (id, posicion) VALUES (1, 0);
