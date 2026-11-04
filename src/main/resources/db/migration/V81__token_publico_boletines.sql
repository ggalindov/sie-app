-- Token público opaco por boletín/documento cacheado, para que el reporte diario por
-- correo (gerencia/abogados) pueda enlazar DIRECTO a cada documento alojado en nuestro
-- propio servidor, en vez de adjuntar cada PDF al correo (pedido explícito del usuario,
-- 2026-11-04, tras ver un correo real con 149 archivos adjuntos: "no se puede enviar asi
-- todos los autos/boletines... mas bien por cada fila que tenga el linkcito directo al
-- documento dentro de nuestro servdor"). No es JWT ni requiere sesión -- mismo criterio
-- que /api/v1/firmas/** (link público de UN solo documento por su identificador opaco,
-- nunca expone el listado completo de boletines del caso).
ALTER TABLE boletines_judiciales_cache ADD COLUMN token_publico VARCHAR(64);

UPDATE boletines_judiciales_cache
SET token_publico = md5(random()::text || clock_timestamp()::text || id::text || 'sie-boletin-token')
WHERE token_publico IS NULL;

ALTER TABLE boletines_judiciales_cache ALTER COLUMN token_publico SET NOT NULL;
ALTER TABLE boletines_judiciales_cache ADD CONSTRAINT uk_boletines_token_publico UNIQUE (token_publico);
