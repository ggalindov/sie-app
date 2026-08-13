-- Códigos de error propios (ERRCODE) que el backend Java mapea a excepciones de negocio:
--   SIE02 = transición de estado no permitida
--   SIE03 = solicitud no encontrada
CREATE OR REPLACE FUNCTION fn_actualizar_estado_solicitud(
    p_id           BIGINT,
    p_nuevo_estado VARCHAR
) RETURNS solicitudes
    LANGUAGE plpgsql
AS
$$
DECLARE
    v_estado_actual VARCHAR(20);
    v_solicitud     solicitudes;
BEGIN
    SELECT estado
    INTO v_estado_actual
    FROM solicitudes
    WHERE id = p_id
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'No existe la solicitud con id %', p_id
            USING ERRCODE = 'SIE03';
    END IF;

    -- transiciones permitidas: NUEVO <-> CONTACTADO, CONTACTADO -> CERRADO.
    -- CERRADO es un estado terminal: no puede pasar directamente a NUEVO ni a CONTACTADO.
    IF NOT (
        (v_estado_actual = 'NUEVO' AND p_nuevo_estado IN ('CONTACTADO', 'CERRADO'))
            OR (v_estado_actual = 'CONTACTADO' AND p_nuevo_estado IN ('NUEVO', 'CERRADO'))
        ) THEN
        RAISE EXCEPTION 'Transición de estado no permitida: % -> %', v_estado_actual, p_nuevo_estado
            USING ERRCODE = 'SIE02';
    END IF;

    UPDATE solicitudes
    SET estado                     = p_nuevo_estado,
        fecha_actualizacion_estado = now()
    WHERE id = p_id
    RETURNING * INTO v_solicitud;

    RETURN v_solicitud;
END;
$$;
