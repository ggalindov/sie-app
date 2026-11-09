package sie.siejuridicos.caso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Pedido explícito del usuario (2026-11-09): "implementa una funcionalidad para todos los
// lunes de la semana a las 8 am que le mande a la gerencia un reporte de casos con fallos,
// radicados no correctos o invalidos, correos y numeros de telefono fallidos o que
// simplemente no han tenido reportes correctos en las ramas". Cron fijo en vez de
// configurable por propiedad (a diferencia de otros schedulers de este proyecto): es un
// requisito explícito y puntual del usuario ("todos los lunes... a las 8 am"), no un valor
// operativo que vaya a necesitar ajustarse seguido.
@Component
public class ReporteCalidadDatosCasosScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReporteCalidadDatosCasosScheduler.class);

    private final ReporteCalidadDatosCasosService service;

    public ReporteCalidadDatosCasosScheduler(ReporteCalidadDatosCasosService service) {
        this.service = service;
    }

    @Scheduled(cron = "0 0 8 * * MON")
    public void enviarReporteSemanal() {
        try {
            service.generarYEnviar();
        } catch (Exception ex) {
            // Nunca debe quedar sin registrar ni tumbar el scheduler -- mismo criterio que
            // ReporteSemanalCasosScheduler: un fallo puntual (ej. SMTP caído) no debe impedir
            // que el próximo lunes se vuelva a intentar.
            log.warn("Fallo inesperado generando el reporte semanal de calidad de datos: {}", ex.getMessage());
        }
    }
}
