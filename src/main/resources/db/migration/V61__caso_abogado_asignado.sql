-- Pedido explícito del usuario: "la idea es desligarnos del excel... asignacion de caso por
-- abogado". El Google Sheets de la firma nunca tuvo (ni podía tener) una columna de "a qué
-- abogado interno le toca este caso" -- es información que solo existe en este sistema, la
-- primera pieza real de desacoplarse de la hoja como única fuente de verdad. Nullable: la
-- mayoría de los 362 casos ya sincronizados no tienen todavía ningún abogado asignado, y el
-- admin los va asignando manualmente desde /admin/casos.
ALTER TABLE casos
    ADD COLUMN id_abogado_asignado BIGINT NULL REFERENCES usuarios_internos(id) ON DELETE SET NULL;

CREATE INDEX idx_casos_abogado_asignado ON casos(id_abogado_asignado);
