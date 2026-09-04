-- Extiende la sincronización automática a 3 hojas de la firma (antes solo "JUDICIALES"):
-- también "SUPERINTENDENCIA" y "PROCESOS COMISARIA-" (ver FuenteCaso, HojaCalculoService).
--
-- fuente identifica de qué pestaña vino cada caso (o MANUAL si se creó a mano desde el
-- panel). Los casos que ya existían antes de esta migración: los que tienen numero_caso ya
-- vienen de la sincronización de JUDICIALES (la única fuente que existía hasta ahora), los
-- que no tienen numero_caso se crearon a mano.
ALTER TABLE casos
    ADD COLUMN fuente VARCHAR(30);

UPDATE casos SET fuente = 'JUDICIALES' WHERE numero_caso IS NOT NULL;
UPDATE casos SET fuente = 'MANUAL' WHERE numero_caso IS NULL;

ALTER TABLE casos
    ALTER COLUMN fuente SET NOT NULL;

-- La unicidad de numero_caso pasa a ser POR FUENTE, no global: "NO. 32" en Judiciales y
-- "NO. 32" en Superintendencia son casos distintos que coinciden en número por pura
-- casualidad (cada hoja numera la suya independientemente), no deben chocar entre sí.
ALTER TABLE casos
    DROP CONSTRAINT uq_casos_numero_caso;

ALTER TABLE casos
    ADD CONSTRAINT uq_casos_fuente_numero_caso UNIQUE (fuente, numero_caso);
