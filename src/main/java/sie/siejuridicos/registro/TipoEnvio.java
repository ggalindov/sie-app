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
    BIENVENIDA_BOLETIN("Bienvenida al boletín");

    private final String nombreVisible;

    TipoEnvio(String nombreVisible) {
        this.nombreVisible = nombreVisible;
    }

    public String getNombreVisible() {
        return nombreVisible;
    }
}
