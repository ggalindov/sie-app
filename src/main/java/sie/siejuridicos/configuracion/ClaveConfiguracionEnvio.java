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
    MARKETING_WHATSAPP("Marketing (WhatsApp)"),
    // Pedido explícito del usuario (2026-11-04): "termina toda la configuracion para que
    // caso que detecte que no tenga auto/boletin retorne peticion al buzon del juzgado" --
    // correo automático al despacho judicial solicitando el documento de una actuación que
    // ningún canal (CPNU exacto ni heurístico de boletines) pudo resolver, con el documento
    // de presentación y autorización del sistema adjunto (ver CorreoDespachoJudicialService).
    // Solo correo: un despacho judicial no recibe WhatsApp.
    CORREO_DESPACHO_JUDICIAL("Correo a despachos judiciales (solicitud de documento)"),
    // Pedido explícito del usuario (2026-11-09): reporte semanal a gerencia (lunes 8am) de
    // calidad de datos -- radicados inválidos, clientes sin correo/teléfono capturado y
    // casos sin proceso identificado en ninguna rama. Ver
    // ReporteCalidadDatosCasosScheduler. Solo correo: es un reporte interno con varias
    // tablas, no pensado para WhatsApp.
    REPORTE_CALIDAD_DATOS_CORREO("Reporte de calidad de datos a gerencia (correo)");

    private final String nombreVisible;

    ClaveConfiguracionEnvio(String nombreVisible) {
        this.nombreVisible = nombreVisible;
    }

    public String getNombreVisible() {
        return nombreVisible;
    }
}
