package sie.siejuridicos.solicitud;

public enum OrigenSolicitud {
    FORMULARIO,
    CHATBOT,
    WHATSAPP,
    // Un abogado o admin agenda una reunión directamente desde el panel (Calendario) para un
    // caso ya existente o para un cliente que todavía no está en el sistema -- pedido
    // explícito del usuario. A diferencia de las demás fuentes, esta solicitud nunca la
    // originó el propio cliente, así que NO dispara los correos/WhatsApp de "recibimos tu
    // solicitud" (ver SolicitudService.crearDirecta) -- el cliente se entera fue con la
    // confirmación de la reunión en sí, cuando se agenda.
    PANEL
}
