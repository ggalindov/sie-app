-- ============================================================================
-- V42: Limpiar histórico y asegurar conteo 100% real de visitas mensuales
-- ============================================================================
-- Elimina los registros semilla artificiales y asegura que tanto el histórico
-- como el mes actual reflejen exclusivamente visitas únicas reales generadas
-- en la plataforma, funcionando de forma continua y acumulativa de ahora en adelante.

-- 1. Eliminar datos sembrados artificialmente
DELETE FROM historico_visitas_mensuales
WHERE anio_mes IN ('2026-04', '2026-05', '2026-06', '2026-07')
   OR (anio_mes = '2026-08' AND total_visitantes = 2140);

-- 2. Asegurar que los meses anteriores realmente existentes queden registrados
INSERT INTO historico_visitas_mensuales (anio_mes, total_visitantes, fecha_cierre)
SELECT anio_mes, count(*), now()
FROM visitas_unicas_mensuales
WHERE anio_mes < to_char(now(), 'YYYY-MM')
GROUP BY anio_mes
ON CONFLICT (anio_mes) DO UPDATE
SET total_visitantes = EXCLUDED.total_visitantes;

-- 3. Actualizar función para retornar SOLO datos 100% reales (sin adiciones artificiales)
CREATE OR REPLACE FUNCTION fn_obtener_historico_visitantes()
RETURNS TABLE(anio_mes VARCHAR(7), total_visitantes BIGINT)
LANGUAGE sql
STABLE
AS $$
    WITH serie_combinada AS (
        -- Meses históricos cerrados reales
        SELECT h.anio_mes, h.total_visitantes
        FROM historico_visitas_mensuales h
        WHERE h.anio_mes < to_char(now(), 'YYYY-MM')

        UNION ALL

        -- Mes actual en curso: visitas vivas reales registradas en visitas_unicas_mensuales
        SELECT to_char(now(), 'YYYY-MM') AS anio_mes,
               COALESCE((SELECT count(*) FROM visitas_unicas_mensuales WHERE anio_mes = to_char(now(), 'YYYY-MM')), 0)::BIGINT AS total_visitantes
    )
    SELECT s.anio_mes, s.total_visitantes
    FROM serie_combinada s
    ORDER BY s.anio_mes ASC;
$$;

-- 4. Actualizar función de cierre mensual para archivar únicamente conteos reales
CREATE OR REPLACE FUNCTION fn_cerrar_mes_visitas()
RETURNS VOID
LANGUAGE plpgsql
AS $$
DECLARE
    r RECORD;
    v_mes_actual VARCHAR(7) := to_char(now(), 'YYYY-MM');
BEGIN
    FOR r IN
        SELECT v.anio_mes, count(*) AS total
        FROM visitas_unicas_mensuales v
        WHERE v.anio_mes < v_mes_actual
        GROUP BY v.anio_mes
    LOOP
        INSERT INTO historico_visitas_mensuales (anio_mes, total_visitantes, fecha_cierre)
        VALUES (r.anio_mes, r.total, now())
        ON CONFLICT (anio_mes) DO UPDATE
        SET total_visitantes = EXCLUDED.total_visitantes,
            fecha_cierre = now();
    END LOOP;
END;
$$;
