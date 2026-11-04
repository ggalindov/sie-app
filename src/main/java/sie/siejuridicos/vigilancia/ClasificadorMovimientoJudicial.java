package sie.siejuridicos.vigilancia;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

// Pedido explícito del usuario (2026-11-04, auditoría en vivo de LUPA): su "Control diario
// procesal" clasifica cada actuación del día en categorías fijas (admisiones, inadmisiones,
// audiencias, terminaciones...) y su "Control asistencia audiencia" marca si el abogado
// asistió. Mismo criterio que ClasificadorActuacionFundamental y los "posible..." de
// ActuacionRegistradaResponse ya existentes en este mismo paquete: palabras clave sobre el
// nombre/anotación de la actuación, NUNCA un hecho verificado -- un abogado debe confirmar
// leyendo el auto real antes de actuar sobre esto. Deliberadamente sin persistir nada nuevo
// (mismo motivo que InferenciaTerminoProcesal): se calcula al vuelo sobre lo que ya está
// guardado, así nunca queda desactualizado respecto a su propia fuente ni exige una
// migración para un dato derivado.
public final class ClasificadorMovimientoJudicial {

    private ClasificadorMovimientoJudicial() {
    }

    // Una sola categoría principal por actuación (a diferencia de los "posible..." de
    // ActuacionRegistradaResponse, que son banderas independientes que pueden coexistir) --
    // para el conteo diario tipo LUPA cada actuación cuenta en un solo cajón, nunca en dos a
    // la vez. Orden de evaluación = prioridad: una actuación que admite Y fija audiencia a la
    // vez (raro, pero posible en un auto compuesto) se cuenta como admisión, el hecho procesal
    // más temprano de los dos.
    public enum TipoMovimiento {
        ADMISION_DEMANDA,
        INADMISION_DEMANDA,
        AUDIENCIA_PROGRAMADA,
        SENTENCIA_FALLO,
        MANDAMIENTO_PAGO,
        TERMINACION_PROCESO
    }

    public enum AsistenciaAudiencia {
        ASISTIO,
        NO_ASISTIO
    }

    public static Optional<TipoMovimiento> clasificarTipo(String nombreActuacion) {
        if (nombreActuacion == null || nombreActuacion.isBlank()) {
            return Optional.empty();
        }
        String n = normalizar(nombreActuacion);

        // "inadmite"/"rechaza" antes que "admite": "auto inadmite" contiene la subcadena
        // "admite", así que el orden de estas dos comprobaciones importa.
        if (n.contains("inadmite") || n.contains("rechaza")) {
            return Optional.of(TipoMovimiento.INADMISION_DEMANDA);
        }
        if (n.contains("admite")) {
            return Optional.of(TipoMovimiento.ADMISION_DEMANDA);
        }
        if (n.contains("fija audiencia") || n.contains("fijación de audiencia") || n.contains("fijacion de audiencia")) {
            return Optional.of(TipoMovimiento.AUDIENCIA_PROGRAMADA);
        }
        if (n.contains("sentencia") || n.contains("fallo")) {
            return Optional.of(TipoMovimiento.SENTENCIA_FALLO);
        }
        if (n.contains("mandamiento de pago") || n.contains("mandamiento ejecutivo")) {
            return Optional.of(TipoMovimiento.MANDAMIENTO_PAGO);
        }
        // "terminación" cubre tanto el archivo normal como la terminación anormal del
        // proceso (desistimiento, transacción, conciliación) -- en ambos casos el proceso
        // deja de estar activo para efectos de este conteo.
        if (n.contains("termina el proceso") || n.contains("terminación del proceso")
                || n.contains("terminacion del proceso") || n.contains("archívese") || n.contains("archivese")) {
            return Optional.of(TipoMovimiento.TERMINACION_PROCESO);
        }
        return Optional.empty();
    }

    // Deliberadamente estricto (exige la palabra "audiencia" en el mismo texto, no solo
    // "compareció"/"inasistencia" sueltos) -- "no compareció" o "inasistencia" aparecen
    // también en contextos sin audiencia (ej. notificaciones personales fallidas), y marcar
    // eso como inasistencia a audiencia sería un falso positivo real, no solo ruidoso: es la
    // misma clase de alerta de cumplimiento que LUPA envía a gerencia, así que un falso
    // positivo aquí puede hacer perder confianza en la alerta real.
    public static Optional<AsistenciaAudiencia> clasificarAsistenciaAudiencia(String nombreActuacion, String anotacion) {
        String texto = normalizar((nombreActuacion == null ? "" : nombreActuacion) + " " + (anotacion == null ? "" : anotacion));
        if (!texto.contains("audiencia")) {
            return Optional.empty();
        }
        if (contieneAlguna(texto,
                "inasistencia", "no compareci", "no asisti", "se declara desierta", "declaro desierta")) {
            return Optional.of(AsistenciaAudiencia.NO_ASISTIO);
        }
        if (contieneAlguna(texto, "se realizó la audiencia", "se realizo la audiencia", "audiencia realizada", "compareci")) {
            return Optional.of(AsistenciaAudiencia.ASISTIO);
        }
        return Optional.empty();
    }

    private static String normalizar(String texto) {
        return texto.toLowerCase(Locale.ROOT);
    }

    private static boolean contieneAlguna(String textoNormalizado, String... claves) {
        for (String clave : claves) {
            if (textoNormalizado.contains(clave)) {
                return true;
            }
        }
        return false;
    }

    public static final List<TipoMovimiento> ORDEN_CONTEO = List.of(
            TipoMovimiento.ADMISION_DEMANDA,
            TipoMovimiento.INADMISION_DEMANDA,
            TipoMovimiento.AUDIENCIA_PROGRAMADA,
            TipoMovimiento.SENTENCIA_FALLO,
            TipoMovimiento.MANDAMIENTO_PAGO,
            TipoMovimiento.TERMINACION_PROCESO);
}
