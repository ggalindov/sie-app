package sie.siejuridicos.caso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import sie.siejuridicos.caso.dto.ResumenEnvioCorreos;

// Notificaciones de radicado pendientes (ver CasoService.enviarCorreosPendientes()): antes
// dependía por completo del botón "Enviar correos pendientes" del panel -- si un lote grande
// excedía el cupo diario compartido de envíos masivos (ver LimiteEnvioMasivoService), el resto
// se quedaba esperando a que alguien recordara volver a hacer clic al día siguiente. Ahora
// también corre solo, a diario, igual que ReporteSemanalCasosScheduler y
// RecordatorioCobroScheduler: este componente solo decide CUÁNDO, toda la lógica real sigue en
// CasoService. El botón manual del panel sigue teniendo sentido para cuando un admin necesita
// el envío reflejado YA, sin esperar al próximo ciclo.
@Component
public class NotificacionesPendientesCasosScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificacionesPendientesCasosScheduler.class);

    private final CasoService casoService;

    public NotificacionesPendientesCasosScheduler(CasoService casoService) {
        this.casoService = casoService;
    }

    @Scheduled(cron = "${app.casos.notificaciones-pendientes-cron}")
    public void enviarNotificacionesPendientes() {
        ResumenEnvioCorreos resumen = casoService.enviarCorreosPendientes();
        log.info("Notificaciones de radicado pendientes: {} correo(s) enviado(s), {} fallido(s); "
                        + "{} WhatsApp enviado(s), {} fallido(s); {} pendiente(s) por el límite diario; "
                        + "{} omitido(s) por no tener un cliente real identificable",
                resumen.correosEnviados(), resumen.correosFallidos(),
                resumen.whatsappEnviados(), resumen.whatsappFallidos(), resumen.pendientesPorLimiteDiario(),
                resumen.omitidosSinClienteReal());
    }
}
