package sie.siejuridicos.cobro.dto;

// Resultado de sincronizar Cobros Pendientes desde el Google Sheets (ver
// CobroService.sincronizarDesdeHoja()). clientesEliminados: cuántas filas que antes existían
// ya no aparecen en la hoja y por eso se borraron del sistema automáticamente (pedido
// explícito: "si se elimina una fila se actualice").
public record ResumenSincronizacionCobros(
        int filasLeidasEnHoja,
        int clientesNuevos,
        int clientesActualizados,
        int clientesEliminados
) {
}
