package sie.siejuridicos.caso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import sie.siejuridicos.caso.dto.ResumenReporteSemanal;

import java.time.DayOfWeek;
import java.time.LocalDate;

// Desacoplado a pedido explícito del usuario:
// - Correo: semanalmente (una vez a la semana, cada lunes) a costo $0 por SMTP.
// - WhatsApp: quincenalmente (dos veces al mes: días 1 y 15) para reducir costos de Meta Cloud API.
// Todos los clientes con un caso activo (radicado ya asignado) reciben un recordatorio para que consulten
// el estado de su proceso.
// Corre a diario a las 8:00 AM (ver app.casos.reporte-semanal-cron), pero SOLO ejecuta el canal autorizado
// para el día exacto: correo los lunes, WhatsApp los días 1 y 15. Si hoy no es ni lunes ni día 1 ni 15,
// la ejecución aborta inmediatamente sin consultar casos ni realizar envíos.
@Component
public class ReporteSemanalCasosScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReporteSemanalCasosScheduler.class);

    private final CasoService casoService;
    private final boolean bloqueoTotalClientes;

    public ReporteSemanalCasosScheduler(CasoService casoService,
                                        @Value("${app.bloqueo-total-clientes:true}") boolean bloqueoTotalClientes) {
        this.casoService = casoService;
        this.bloqueoTotalClientes = bloqueoTotalClientes;
    }

    @Scheduled(cron = "${app.casos.reporte-semanal-cron}")
    public void enviarReporteSemanal() {
        if (bloqueoTotalClientes) {
            log.info("[SEGURIDAD ACTIVA] ReporteSemanalCasosScheduler OMITIDO por app.bloqueo-total-clientes=true. Cero mensajes a clientes.");
            return;
        }
        LocalDate hoy = LocalDate.now();
        boolean esLunes = hoy.getDayOfWeek() == DayOfWeek.MONDAY;
        boolean esMartes = hoy.getDayOfWeek() == DayOfWeek.TUESDAY;
        int diaDelMes = hoy.getDayOfMonth();
        boolean esDiaWhatsapp = (diaDelMes == 1 || diaDelMes == 2 || diaDelMes == 15 || diaDelMes == 16);
        boolean permitirCorreo = esLunes || esMartes;

        if (!permitirCorreo && !esDiaWhatsapp) {
            log.info("Reporte periódico de casos: hoy {} (día {}) no es día programado de envío (Correo: lunes y martes de remanentes, WhatsApp: 1-2 y 15-16). Se omite.",
                    hoy.getDayOfWeek(), diaDelMes);
            return;
        }

        ResumenReporteSemanal resumen = casoService.enviarReporteSemanalAutomatico(permitirCorreo, esDiaWhatsapp);
        log.info("Reporte periódico de casos (Correo: {}, WhatsApp: {}): {} caso(s) cubiertos, {} correo(s) enviado(s), "
                        + "{} fallido(s); {} WhatsApp enviado(s), {} fallido(s); {} pendiente(s) por el límite diario; "
                        + "{} omitido(s) por no tener un cliente real identificable",
                permitirCorreo, esDiaWhatsapp,
                resumen.casosConReporte(), resumen.correosEnviados(), resumen.correosFallidos(),
                resumen.whatsappEnviados(), resumen.whatsappFallidos(), resumen.pendientesPorLimiteDiario(),
                resumen.omitidosSinClienteReal());
    }
}
