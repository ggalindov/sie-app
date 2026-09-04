package sie.siejuridicos.cobro.dto;

// Resultado del envío mensual de recordatorios de cobro (ver
// CobroService.enviarRecordatorios()), disparado por el scheduler del día 1 o por el botón
// manual del panel. clientesSinCosto: cuántos se saltaron por tener honorarios en $0 (pedido
// explícito: "todo cliente que tenga 0 en casilla de honorario saltarlo").
public record ResumenEnvioRecordatoriosCobros(
        int correosEnviados,
        int correosFallidos,
        int whatsappEnviados,
        int whatsappFallidos,
        int clientesSinCosto
) {
}
