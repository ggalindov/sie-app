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
    // Auditoría 2026-11-06, pedido explícito del usuario ("de ninguna forma se pueda
    // vulnerar"): a diferencia de ALERTA_INTEGRIDAD_DOCUMENTO (un documento puntual), esta
    // es la alerta de máxima severidad cuando el ancla diaria externa (RFC 3161) ya no
    // coincide con los checkpoints actuales -- ver AnclaDiariaIntegridadScheduler.
    ALERTA_INTEGRIDAD_ANCLA_DIARIA("Alerta crítica de integridad del ancla diaria"),
    // PQRS (Peticiones, Quejas, Reclamos y Sugerencias): mismo patrón que CODIGO_CASO (el
    // radicado es la única forma de consultar el estado, sin cuenta ni login).
    RADICADO_PQRS("Radicado de PQRS"),
    RESPUESTA_PQRS("Respuesta a PQRS"),
    // Seguimiento judicial (ver sie.siejuridicos.vigilancia): envío manual, a pedido
    // expreso de un abogado, de la última providencia de fondo encontrada en Publicaciones
    // Procesales -- nunca automático.
    PROVIDENCIA_JUDICIAL("Providencia judicial"),
    // Digest AUTOMÁTICO de boletines judiciales nuevos (pedido explícito del usuario, ver
    // sie.siejuridicos.vigilancia.BoletinJudicialDigestService) -- distinto de BOLETIN (ese
    // es el newsletter de marketing a suscriptores) y de PROVIDENCIA_JUDICIAL (ese es el
    // envío manual de un documento puntual a pedido de un abogado).
    BOLETIN_JUDICIAL("Boletín judicial"),
    // Auditoría de SIE-Vigilancia (pedido explícito del usuario: "implementa... alertas,
    // anuncios de caidas") -- correo automático a todo ADMIN_GENERAL activo cuando
    // RamaJudicialCpnuClient/PublicacionesProcesalesClient/SamaiClient "pareceCaido()" tras
    // un paso del ciclo nocturno, ver VigilanciaCaidaAlertaListener.
    ALERTA_VIGILANCIA_CAIDA("Alerta de caída de vigilancia judicial"),
    // Pedido explícito del usuario (2026-11-04, replicando el flujo real de LUPA Jurídica):
    // correo saliente a un despacho judicial solicitando el documento de una actuación que
    // ningún canal automático pudo resolver -- ver CorreoDespachoJudicialService.
    CORREO_DESPACHO_JUDICIAL("Correo a despacho judicial");

    private final String nombreVisible;

    TipoEnvio(String nombreVisible) {
        this.nombreVisible = nombreVisible;
    }

    public String getNombreVisible() {
        return nombreVisible;
    }
}
