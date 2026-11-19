-- Caida de produccion 2026-11-19: la columna contenido_bytes (324 MB en 395 filas) se cargaba
-- completa en memoria con cualquier consulta sobre BoletinJudicialCache (calendario, listados,
-- digests) y agotaba el heap de la JVM. Desde esta version la entidad ya NO mapea la columna:
-- los bytes se escriben y leen solo con consultas nativas dedicadas (ver
-- BoletinJudicialCacheRepository.guardarConContenido / leerContenido).
-- El INSERT de la entidad ya no incluye la columna, asi que debe admitir NULL un instante
-- (se rellena en la misma transaccion).
ALTER TABLE boletines_judiciales_cache ALTER COLUMN contenido_bytes DROP NOT NULL;
