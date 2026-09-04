-- Reestructuración de "casos": el estado del caso ya no se gestiona en esta base de datos.
-- La firma lleva el seguimiento real de sus casos en un Google Sheets propio, y esta tabla
-- pasa a ser solo el registro de "qué radicado le pertenece a qué cliente" para poder
-- enviarle el código por correo. Consultar el estado en vivo pasa a leer ese Google Sheets
-- (ver HojaCalculoService), buscando la fila cuyo Radicado ID coincide con radicado_id.
--
-- codigo_unico (generado por nosotros, SIE-2026-XXXXXX) se renombra a radicado_id porque
-- deja de ser un código nuestro: ahora el admin escribe ahí el radicado judicial real que ya
-- existe (o existirá) en la hoja de la firma, para que la búsqueda por columna I siempre
-- encuentre la fila correcta.
ALTER TABLE casos
    RENAME COLUMN codigo_unico TO radicado_id;

ALTER INDEX ix_casos_codigo_unico RENAME TO ix_casos_radicado_id;

ALTER TABLE casos
    DROP CONSTRAINT ck_casos_etapa;

ALTER TABLE casos
    DROP COLUMN etapa;

ALTER TABLE casos
    DROP CONSTRAINT casos_id_categoria_fkey;

ALTER TABLE casos
    DROP COLUMN id_categoria;

-- Ya no hay flujo que la actualice (se eliminó el endpoint de cambiar etapa): dejarla
-- quieta con su valor de creación sería engañoso, mejor quitarla.
ALTER TABLE casos
    DROP COLUMN fecha_actualizacion;
