-- Conteo de visitantes únicos aproximados por mes, para el contador grande del panel
-- administrativo. Un "visitante" es un identificador anónimo generado en el navegador
-- (ver frontend, localStorage) — no es una cookie de terceros, no rastrea entre sitios y
-- no lleva ningún dato personal ni la IP asociada, solo un UUID aleatorio y el mes en que
-- se vio por primera vez. Es lo mínimo necesario para deduplicar sin identificar a nadie
-- (Ley 1581 de 2012, mismo criterio ya aplicado en el resto del proyecto).
CREATE TABLE visitas_unicas_mensuales (
    id BIGSERIAL PRIMARY KEY,
    anio_mes VARCHAR(7) NOT NULL,      -- 'YYYY-MM'
    visitante_id VARCHAR(64) NOT NULL, -- UUID anónimo generado en el navegador
    primera_vez TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (anio_mes, visitante_id)
);

-- Registro idempotente: si ese visitante ya se contó este mes, no hace nada (ON CONFLICT
-- DO NOTHING, sin lanzar error) — el frontend puede reenviar el mismo id varias veces en
-- el mes sin inflar el conteo. Devuelve true solo si de verdad insertó una fila nueva.
CREATE OR REPLACE FUNCTION fn_registrar_visita_unica(p_visitante_id VARCHAR(64))
    RETURNS BOOLEAN
    LANGUAGE sql
AS
$$
WITH insertado AS (
    INSERT INTO visitas_unicas_mensuales (anio_mes, visitante_id)
    VALUES (to_char(now(), 'YYYY-MM'), p_visitante_id)
    ON CONFLICT (anio_mes, visitante_id) DO NOTHING
    RETURNING true
)
SELECT COALESCE((SELECT * FROM insertado), false);
$$;

-- Cantidad de visitantes únicos distintos este mes calendario. 0 si todavía no hubo
-- ninguno (no existe fila).
CREATE OR REPLACE FUNCTION fn_contar_visitantes_mes_actual()
    RETURNS BIGINT
    LANGUAGE sql
AS
$$
SELECT count(*) FROM visitas_unicas_mensuales
WHERE anio_mes = to_char(now(), 'YYYY-MM');
$$;
