package sie.siejuridicos.cobro.dto;

// Resultado del envío mensual de recordatorios de cobro (ver
// CobroService.enviarRecordatorios()), disparado diariamente por el scheduler (ver
// RecordatorioCobroScheduler) o por el botón manual del panel. clientesSinCosto: cuántos se
// saltaron por tener honorarios en $0 (pedido explícito: "todo cliente que tenga 0 en casilla
// de honorario saltarlo").
public record ResumenEnvioRecordatoriosCobros(
        int correosEnviados,
        int correosFallidos,
        int whatsappEnviados,
        int whatsappFallidos,
        int clientesSinCosto,
        // Cuántos clientes pendientes de recordatorio este mes NI SIQUIERA se intentaron
        // porque ya se alcanzó el cupo diario compartido de envíos masivos (ver
        // LimiteEnvioMasivoService). Quedan sin marcar (fechaUltimoRecordatorio sin
        // actualizar) para que la corrida automática del día siguiente los recoja.
        int pendientesPorLimiteDiario
) {
}
