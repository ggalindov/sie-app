package sie.siejuridicos.vigilancia.dto;

// Resumen de una corrida de SeguimientoJudicialService.sincronizarTodos() -- mismo patrón
// que ResumenSincronizacion (sincronización de casos desde el Excel), pensado para loguearse
// y para la respuesta del botón manual del panel.
public record ResumenSeguimientoJudicial(
        int casosRevisados,
        int casosConProcesoCpnu,
        int casosSinProcesoCpnu,
        int actuacionesNuevas,
        int actuacionesFundamentalesNuevas
) {
}
