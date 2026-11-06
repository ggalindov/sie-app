package sie.siejuridicos.cobro.dto;

import java.time.LocalDateTime;

// Pedido explícito del usuario (2026-11-06): "revisa porque el aceptar o rechazar pagos en
// whatsapp no se esta reflejando" -- auditoría en producción confirmó que el problema real es
// que Meta nunca llegó a entregar un evento real al webhook (configuración externa del Meta
// Business Manager, fuera del alcance de este sistema), no un bug en el procesamiento. Este
// endpoint deja ese hecho visible y monitoreable desde /admin/cobros en vez de que vuelva a
// pasar inadvertido: ultimaRespuestaReal es null si NUNCA se ha recibido un evento real (solo
// cuenta lo que entra por WhatsAppWebhookController, nunca /simular-respuesta ni el ajuste
// manual del admin).
public record SaludWebhookWhatsAppResponse(LocalDateTime ultimaRespuestaReal) {
}
