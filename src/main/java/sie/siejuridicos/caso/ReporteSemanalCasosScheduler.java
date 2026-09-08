package sie.siejuridicos.caso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import sie.siejuridicos.caso.dto.ResumenReporteSemanal;

// Pedido explícito del usuario: una vez a la semana, todos los clientes con un caso activo
// (radicado ya asignado) reciben un recordatorio -- por correo y WhatsApp -- para que
// consulten el estado de su proceso, ya que puede haber novedades esa semana. Mismo patrón
// que RecordatorioCitaScheduler/RecordatorioCobroScheduler: este componente solo decide
// CUÁNDO, toda la lógica real vive en CasoService.enviarReporteSemanal().
@Component
public class ReporteSemanalCasosScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReporteSemanalCasosScheduler.class);

    private final CasoService casoService;

    public ReporteSemanalCasosScheduler(CasoService casoService) {
        this.casoService = casoService;
    }

    @Scheduled(cron = "${app.casos.reporte-semanal-cron}")
    public void enviarReporteSemanal() {
        ResumenReporteSemanal resumen = casoService.enviarReporteSemanal();
        log.info("Reporte semanal de casos: {} caso(s) con reporte, {} correo(s) enviado(s), "
                        + "{} fallido(s); {} WhatsApp enviado(s), {} fallido(s)",
                resumen.casosConReporte(), resumen.correosEnviados(), resumen.correosFallidos(),
                resumen.whatsappEnviados(), resumen.whatsappFallidos());
    }
}
