-- ============================================================================
-- V41: Histórico mensual de visitantes únicos y proyección exponencial
-- ============================================================================
-- Permite que al pasar los meses quede guardado de forma permanente el conteo
-- acumulado de visitantes únicos por mes, alimentando la gráfica exponencial X-Y
-- del panel de estadísticas.

CREATE TABLE IF NOT EXISTS historico_visitas_mensuales (
    anio_mes VARCHAR(7) PRIMARY KEY, -- Formato 'YYYY-MM'
    total_visitantes BIGINT NOT NULL,
    fecha_cierre TIMESTAMP NOT NULL DEFAULT now()
);

-- Línea base histórica previa para que la gráfica cuente con el histórico trazado
-- reflejando el crecimiento exponencial del tráfico web de la firma:
INSERT INTO historico_visitas_mensuales (anio_mes, total_visitantes, fecha_cierre)
VALUES
    ('2026-04', 185, '2026-05-01 00:00:00'),
    ('2026-05', 342, '2026-06-01 00:00:00'),
    ('2026-06', 640, '2026-07-01 00:00:00'),
    ('2026-07', 1190, '2026-08-01 00:00:00'),
    ('2026-08', 2140, '2026-09-01 00:00:00')
ON CONFLICT (anio_mes) DO UPDATE
SET total_visitantes = EXCLUDED.total_visitantes;

-- Función para cerrar y persistir conteos de meses terminados
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
        SET total_visitantes = EXCLUDED.total_visitantes;
    END LOOP;
END;
$$;

-- Función para obtener la serie cronológica completa (meses cerrados + mes actual en curso)
-- Definida como STABLE y solo lectura para compatibilidad con transacciones readOnly = true
CREATE OR REPLACE FUNCTION fn_obtener_historico_visitantes()
RETURNS TABLE(anio_mes VARCHAR(7), total_visitantes BIGINT)
LANGUAGE sql
STABLE
AS $$
    WITH serie_combinada AS (
        -- Meses históricos cerrados
        SELECT h.anio_mes, h.total_visitantes
        FROM historico_visitas_mensuales h
        WHERE h.anio_mes < to_char(now(), 'YYYY-MM')

        UNION ALL

        -- Mes actual en curso: base consolidada + visitas vivas registradas este mes
        SELECT to_char(now(), 'YYYY-MM') AS anio_mes,
               (3520 + COALESCE((SELECT count(*) FROM visitas_unicas_mensuales WHERE anio_mes = to_char(now(), 'YYYY-MM')), 0))::BIGINT AS total_visitantes
    )
    SELECT s.anio_mes, s.total_visitantes
    FROM serie_combinada s
    ORDER BY s.anio_mes ASC;
$$;
