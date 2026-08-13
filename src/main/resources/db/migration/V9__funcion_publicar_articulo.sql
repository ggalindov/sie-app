-- Códigos de error propios (ERRCODE) que el backend Java mapea a excepciones de negocio:
--   SIE04 = artículo incompleto para publicar
--   SIE05 = artículo no encontrado
CREATE OR REPLACE FUNCTION fn_publicar_articulo(p_id BIGINT)
    RETURNS articulos
    LANGUAGE plpgsql
AS
$$
DECLARE
    v_articulo articulos;
BEGIN
    SELECT * INTO v_articulo FROM articulos WHERE id = p_id FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'No existe el artículo con id %', p_id
            USING ERRCODE = 'SIE05';
    END IF;

    IF v_articulo.titulo IS NULL OR btrim(v_articulo.titulo) = ''
        OR v_articulo.contenido IS NULL OR btrim(v_articulo.contenido) = ''
        OR v_articulo.id_categoria IS NULL THEN
        RAISE EXCEPTION 'El artículo % no tiene título, contenido o categoría completos para publicarse', p_id
            USING ERRCODE = 'SIE04';
    END IF;

    UPDATE articulos
    SET estado            = 'PUBLICADO',
        fecha_publicacion = now()
    WHERE id = p_id
    RETURNING * INTO v_articulo;

    RETURN v_articulo;
END;
$$;
