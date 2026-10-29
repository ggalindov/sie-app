package sie.siejuridicos.registro;

// Qué clase de mensaje es, sin importar el canal (el mismo tipo puede salir por correo o por
// WhatsApp, ej. CONFIRMACION_CITA) -- ver RegistroEnvio y RegistroEnvioService.registrar(),
// llamado desde EmailService/WhatsAppService justo después de cada intento de envío real.
public enum TipoEnvio {
    CODIGO_CASO("Código de caso"),
    REPORTE_CASO("Reporte de caso"),
    RECORDATORIO_COBRO("Recordatorio de cobro"),
    CONFIRMACION_CITA("Confirmación de cita"),
    RECORDATORIO_CITA("Recordatorio de cita"),
    SOLICITUD_CONFIRMACION("Confirmación de solicitud"),
    SOLICITUD_RESPUESTA("Respuesta a solicitud"),
    // Correos/WhatsApp que van hacia adentro de la firma (admin, línea interna), no hacia un
    // cliente: aviso de nueva solicitud, aviso de nueva cita agendada.
    NOTIFICACION_INTERNA("Notificación interna"),
    PUBLICACION_BLOG("Aviso de publicación"),
    AVISO_REDES_SOCIALES("Aviso a redes sociales"),
    BOLETIN("Boletín"),
    BIENVENIDA_BOLETIN("Bienvenida al boletín"),
    // Motor de firma electrónica (ver sie.siejuridicos.documento): pedido explícito del
    // usuario de llevar registro de correos/WhatsApp salientes y para quién, extendido
    // también a los envíos de este nuevo módulo en vez de duplicar una segunda bitácora.
    ENLACE_FIRMA_DOCUMENTO("Enlace de firma de documento"),
    OTP_FIRMA_DOCUMENTO("Código de verificación de firma"),
    DOCUMENTO_FIRMADO_FINAL("Documento firmado final"),
    RECORDATORIO_FIRMA("Recordatorio de firma pendiente"),
    ALERTA_INTEGRIDAD_DOCUMENTO("Alerta de integridad de documento"),
    ALERTA_FIRMA_RECHAZADA("Alerta de firma rechazada"),
    // PQRS (Peticiones, Quejas, Reclamos y Sugerencias): mismo patrón que CODIGO_CASO (el
    // radicado es la única forma de consultar el estado, sin cuenta ni login).
    RADICADO_PQRS("Radicado de PQRS"),
    RESPUESTA_PQRS("Respuesta a PQRS");

    private final String nombreVisible;

    TipoEnvio(String nombreVisible) {
        this.nombreVisible = nombreVisible;
    }

    public String getNombreVisible() {
        return nombreVisible;
    }
}
