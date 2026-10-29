-- Hasta ahora el carácter "append-only" de documento_eventos (cadena de custodia legal del
-- motor de firma) dependía por completo de que el código Java nunca llamara UPDATE/DELETE
-- sobre esa entidad (no hay esos métodos expuestos en el repositorio salvo el borrado
-- completo de un documento CANCELADO/ANULADO, ver más abajo) -- pero nada en la base de
-- datos lo impedía: cualquiera con acceso directo a Postgres, o un bug futuro, podía alterar
-- o borrar filas sin que quedara ninguna señal. Este trigger lo convierte en una garantía
-- real de la base de datos, no solo una convención de la aplicación.
--
-- Única excepción legítima: DocumentoAdminService#eliminar borra TODOS los eventos de un
-- documento que nunca llegó a completarse (solo CANCELADO o ANULADO, nunca uno ya SELLADO/
-- ENCRIPTADO -- ver comentario de ese método) como parte de un borrado total y deliberado
-- del documento completo, no una alteración selectiva de su historial. Esa única vía marca
-- la variable de sesión 'app.documento_eventos.permitir_borrado' = 'on' con SET LOCAL
-- (vive solo dentro de esa transacción, se resetea sola al terminar) justo antes de borrar;
-- cualquier otro intento de UPDATE/DELETE, sin esa marca explícita, se rechaza.
CREATE OR REPLACE FUNCTION fn_rechazar_modificacion_tabla_append_only()
    RETURNS TRIGGER
    LANGUAGE plpgsql
AS
$$
BEGIN
    IF current_setting('app.documento_eventos.permitir_borrado', true) = 'on' THEN
        RETURN COALESCE(NEW, OLD);
    END IF;
    RAISE EXCEPTION 'La tabla % es de solo-agregar (append-only): % no está permitido sobre una fila ya insertada.',
        TG_TABLE_NAME, TG_OP;
END;
$$;

CREATE TRIGGER trg_documento_eventos_append_only
    BEFORE UPDATE OR DELETE ON documento_eventos
    FOR EACH ROW
    EXECUTE FUNCTION fn_rechazar_modificacion_tabla_append_only();
