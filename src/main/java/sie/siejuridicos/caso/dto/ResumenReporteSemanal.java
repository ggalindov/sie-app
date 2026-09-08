package sie.siejuridicos.caso.dto;

// Resultado del reporte semanal a todos los clientes con caso activo (ver
// CasoService.enviarReporteSemanal()) -- mismo criterio de conteo por canal que
// ResumenEnvioCorreos: un cliente puede tener correo pero no teléfono válido, o viceversa.
public record ResumenReporteSemanal(
        int casosConReporte,
        int correosEnviados,
        int correosFallidos,
        int whatsappEnviados,
        int whatsappFallidos
) {
}
