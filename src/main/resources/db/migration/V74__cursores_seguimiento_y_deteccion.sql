-- Mismo bug real que ya se arregló en boletines (ver V71, VigilanciaCursorBoletines) pero
-- nunca se había aplicado a los otros 2 pasos del ciclo nocturno (confirmado en vivo
-- 2026-11-01, auditoría de boletines): ni SeguimientoJudicialService ni
-- DeteccionProcesosNuevosService tenían cursor rotativo -- si el circuit breaker del CPNU
-- corta cualquiera de los dos a mitad de camino, SIEMPRE se corta en el mismo punto relativo
-- de la lista, y los últimos casos/clientes nunca reciben su turno.
CREATE TABLE vigilancia_cursor_seguimiento (
    id SMALLINT PRIMARY KEY,
    posicion INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT vigilancia_cursor_seguimiento_una_fila CHECK (id = 1)
);
INSERT INTO vigilancia_cursor_seguimiento (id, posicion) VALUES (1, 0);

CREATE TABLE vigilancia_cursor_deteccion (
    id SMALLINT PRIMARY KEY,
    posicion INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT vigilancia_cursor_deteccion_una_fila CHECK (id = 1)
);
INSERT INTO vigilancia_cursor_deteccion (id, posicion) VALUES (1, 0);
