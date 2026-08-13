CREATE OR REPLACE FUNCTION fn_contar_conversaciones_mes_actual()
    RETURNS INTEGER
    LANGUAGE sql
AS
$$
SELECT COUNT(*)::INTEGER
FROM conversaciones_chatbot
WHERE date_trunc('month', fecha) = date_trunc('month', now());
$$;
