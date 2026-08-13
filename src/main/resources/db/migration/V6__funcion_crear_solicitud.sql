-- Códigos de error propios (ERRCODE) que el backend Java mapea a excepciones de negocio:
--   SIE01 = solicitud duplicada en las últimas 24h
CREATE OR REPLACE FUNCTION fn_crear_solicitud(
    p_nombre   VARCHAR,
    p_correo   VARCHAR,
    p_telefono VARCHAR,
    p_mensaje  TEXT,
    p_origen   VARCHAR
) RETURNS solicitudes
    LANGUAGE plpgsql
AS
$$
DECLARE
    v_solicitud solicitudes;
BEGIN
    IF EXISTS (SELECT 1
               FROM solicitudes
               WHERE correo = p_correo
                 AND mensaje = p_mensaje
                 AND fecha_creacion >= now() - INTERVAL '24 hours') THEN
        RAISE EXCEPTION 'Ya existe una solicitud idéntica de % en las últimas 24 horas', p_correo
            USING ERRCODE = 'SIE01';
    END IF;

    INSERT INTO solicitudes (nombre, correo, telefono, mensaje, origen)
    VALUES (p_nombre, p_correo, p_telefono, p_mensaje, p_origen)
    RETURNING * INTO v_solicitud;

    RETURN v_solicitud;
END;
$$;
