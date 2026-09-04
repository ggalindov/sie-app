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
        int whatsappFallidos
) {
}
