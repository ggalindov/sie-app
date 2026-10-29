-- Prueba auditable de vigilancia continua (DocumentIntegrityScanner, ahora cada hora en vez
-- de cada 6h): antes, cada corrida del barrido periódico solo dejaba rastro en logs SLF4J
-- (efímero, se pierde al rotar logs) y únicamente se guardaba algo en base de datos cuando
-- el barrido SÍ encontraba una alteración. Ante un juez o una auditoría externa, eso no
-- demuestra que el sistema estuvo vigilando de forma continua -- solo que, SI algo falló,
-- quedó registrado. Esta tabla registra TODA corrida, limpia o no, como evidencia de que la
-- vigilancia estuvo activa de forma ininterrumpida.
CREATE TABLE integridad_barridos (
    id                     BIGSERIAL PRIMARY KEY,
    ejecutado_en           TIMESTAMP NOT NULL,
    documentos_revisados   INT NOT NULL,
    alteraciones_encontradas INT NOT NULL,
    duracion_ms            BIGINT NOT NULL
);

CREATE INDEX idx_integridad_barridos_ejecutado_en ON integridad_barridos (ejecutado_en);
