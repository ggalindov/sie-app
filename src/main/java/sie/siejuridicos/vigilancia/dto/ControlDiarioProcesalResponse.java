package sie.siejuridicos.vigilancia.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

// Réplica del "Control diario procesal" de LUPA (pedido explícito del usuario, 2026-11-04,
// tras auditar en vivo su panel: "Procesos Nuevos(Admisiones)", "Procesos Nuevos(No
// Admisiones)", "Audiencias", "Procesos Terminados", "Vencimientos de Terminos", todo
// filtrable por rango de fecha). conteosPorTipo usa el nombre del enum
// ClasificadorMovimientoJudicial.TipoMovimiento como llave (ADMISION_DEMANDA,
// INADMISION_DEMANDA, AUDIENCIA_PROGRAMADA, SENTENCIA_FALLO, MANDAMIENTO_PAGO,
// TERMINACION_PROCESO) para que el frontend no tenga que mantener la lista de categorías
// duplicada -- siempre trae las 6, con 0 cuando no hubo ninguna en el rango.
public record ControlDiarioProcesalResponse(
        LocalDate desde,
        LocalDate hasta,
        Map<String, Integer> conteosPorTipo,
        int vencimientosTermino,
        List<AudienciaAlerta> audienciasConPosibleInasistencia
) {
    // Pedido explícito del usuario: LUPA alerta "Procesos en donde el abogado no asistió a
    // la audiencia" -- esta es la misma alerta, derivada por palabras clave (ver
    // ClasificadorMovimientoJudicial.clasificarAsistenciaAudiencia), nunca un hecho
    // verificado: siempre hay que confirmar leyendo el acta real.
    public record AudienciaAlerta(
            Long casoId,
            String nombreCliente,
            String radicadoId,
            String nombreActuacion,
            String anotacion,
            LocalDateTime fechaRegistro,
            String abogadoAsignadoNombre
    ) {
    }
}
