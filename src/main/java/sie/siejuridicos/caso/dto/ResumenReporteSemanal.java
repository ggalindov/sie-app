package sie.siejuridicos.caso.dto;

// Resultado del reporte semanal a todos los clientes con caso activo (ver
// CasoService.enviarReporteSemanal()) -- mismo criterio de conteo por canal que
// ResumenEnvioCorreos: un cliente puede tener correo pero no teléfono válido, o viceversa.
public record ResumenReporteSemanal(
        int casosConReporte,
        int correosEnviados,
        int correosFallidos,
        int whatsappEnviados,
        int whatsappFallidos,
        // Cuántos casos pendientes de esta semana NI SIQUIERA se intentaron porque ya se
        // alcanzó el cupo diario compartido de envíos masivos (ver LimiteEnvioMasivoService).
        // Quedan sin marcar (fechaUltimoReporteSemanal sin actualizar) para que la corrida
        // automática del día siguiente los recoja.
        int pendientesPorLimiteDiario,
        // Igual que en ResumenEnvioCorreos: casos omitidos por tener una nota administrativa
        // en vez de un cliente real en el nombre -- requieren revisión manual.
        int omitidosSinClienteReal
) {
}
