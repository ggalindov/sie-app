package sie.siejuridicos.caso.dto;

// Resultado de disparar el envío en bloque de las notificaciones pendientes (ver
// CasoService.enviarCorreosPendientes()) -- por los dos canales, correo y WhatsApp,
// contados por separado: un caso puede necesitar solo uno de los dos (o ninguno más, si ya
// tenía ambos al día).
public record ResumenEnvioCorreos(
        int correosEnviados,
        // Fallidos de verdad (incluyendo el reintento automático, ver
        // CasoService.enviarCorreosPendientes()): el caso queda pendiente para el próximo
        // intento, nunca se marca "enviado" sin haber llegado.
        int correosFallidos,
        int whatsappEnviados,
        int whatsappFallidos,
        // Cuántos casos NI SIQUIERA se intentaron en esta corrida porque ya se alcanzó el
        // cupo diario compartido de envíos masivos (ver LimiteEnvioMasivoService) -- distinto
        // de correosFallidos/whatsappFallidos (esos sí se intentaron y fallaron de verdad).
        // Quedan tal cual estaban (sin marcar como enviados) para que la próxima corrida
        // automática, al día siguiente, los recoja sin perder a nadie.
        int pendientesPorLimiteDiario,
        // Casos cuyo nombre en la hoja es en realidad una nota administrativa interna del
        // despacho ("no somos parte", etc.) en vez de un cliente real -- ver
        // CasoService.contieneNotaAdministrativaNoCliente(). Se omiten por completo (ningún
        // canal) y requieren revisión manual; no cuentan como fallidos ni como pendientes por
        // cupo.
        int omitidosSinClienteReal
) {
}
