package sie.siejuridicos.configuracion;

// Pedido explícito del usuario: "boton independiente notificaciones, boletin, marketing
// entre otros" + "apagar los envios de correo, apagar los envios de whatsapp" -- cada
// combinación categoría+canal es un interruptor independiente que el admin puede
// prender/apagar en caliente desde /admin (ver migración V62, ConfiguracionEnvio). Esto es
// ADEMÁS de app.bloqueo-total-clientes (el interruptor maestro de seguridad, estático, ese
// sigue existiendo tal cual) -- ambos se consultan: primero el maestro, luego este.
public enum ClaveConfiguracionEnvio {
    // Código de radicado al crear/sincronizar un caso, confirmaciones de cita, etc. -- lo
    // que ya existía antes de este interruptor.
    NOTIFICACIONES_CORREO("Notificaciones (correo)"),
    NOTIFICACIONES_WHATSAPP("Notificaciones (WhatsApp)"),
    // El reporte semanal/quincenal de estado del caso (ver CasoService.enviarReporteSemanal).
    REPORTE_PERIODICO_CORREO("Reporte periódico (correo)"),
    REPORTE_PERIODICO_WHATSAPP("Reporte periódico (WhatsApp)"),
    // Gap real detectado en la auditoría del plan de desarrollo: digest diario (gerente y
    // abogados) y semanal (clientes) de movimientos/boletines judiciales nuevos -- ver
    // sie.siejuridicos.vigilancia.BoletinJudicialDigestService.
    BOLETIN_JUDICIAL_CORREO("Boletín judicial (correo)"),
    BOLETIN_JUDICIAL_WHATSAPP("Boletín judicial (WhatsApp)"),
    // Avisos de blog/newsletter a suscriptores de marketing.
    MARKETING_CORREO("Marketing (correo)"),
    MARKETING_WHATSAPP("Marketing (WhatsApp)");

    private final String nombreVisible;

    ClaveConfiguracionEnvio(String nombreVisible) {
        this.nombreVisible = nombreVisible;
    }

    public String getNombreVisible() {
        return nombreVisible;
    }
}
