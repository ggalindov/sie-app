package sie.siejuridicos.common.exception;

import org.springframework.dao.DataAccessException;

import java.sql.SQLException;

/**
 * Traduce los ERRCODE personalizados (SIE01-SIE05) que lanzan las funciones PL/pgSQL
 * (fn_crear_solicitud, fn_actualizar_estado_solicitud, fn_publicar_articulo) en excepciones
 * de negocio legibles por el GlobalExceptionHandler.
 */
public final class ErroresBaseDatos {

    private ErroresBaseDatos() {
    }

    public static RuntimeException traducir(DataAccessException ex) {
        SQLException sqlEx = extraerSqlException(ex);
        if (sqlEx == null || sqlEx.getSQLState() == null) {
            return ex;
        }
        String mensaje = mensajeLimpio(sqlEx);
        return switch (sqlEx.getSQLState()) {
            case "SIE01", "SIE02" -> new ConflictoNegocioException(mensaje);
            case "SIE03", "SIE05" -> new RecursoNoEncontradoException(mensaje);
            case "SIE04" -> new EntidadInvalidaException(mensaje);
            default -> ex;
        };
    }

    private static SQLException extraerSqlException(Throwable t) {
        while (t != null) {
            if (t instanceof SQLException se) {
                return se;
            }
            t = t.getCause();
        }
        return null;
    }

    private static String mensajeLimpio(SQLException sqlEx) {
        String mensaje = sqlEx.getMessage();
        int idx = mensaje.indexOf("ERROR:");
        return idx >= 0 ? mensaje.substring(idx + "ERROR:".length()).trim() : mensaje;
    }
}
