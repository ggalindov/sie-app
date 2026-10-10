package sie.siejuridicos.cobro;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Corrige el bug real reportado por el usuario ("no se está limpiando esos check antes de que
// se acabe el mes para que no se bugee la información"): corre temprano el día 1 de cada mes,
// antes de que RecordatorioCobroScheduler empiece a enviar (día 3), y limpia pagoEsteMes/
// respondioMensaje de cualquier cliente cuya respuesta quedó de un mes anterior -- ver
// CobroService.reiniciarEstadoMensual(). No envía ningún mensaje a clientes (solo limpia estado
// interno y el Google Sheets), así que, a diferencia de RecordatorioCobroScheduler, no necesita
// el guardado app.bloqueo-total-clientes.
@Component
public class ReinicioMensualCobrosScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReinicioMensualCobrosScheduler.class);

    private final CobroService cobroService;

    public ReinicioMensualCobrosScheduler(CobroService cobroService) {
        this.cobroService = cobroService;
    }

    @Scheduled(cron = "${app.cobros.reinicio-mensual-cron}")
    public void reiniciarEstadoDelMes() {
        int reiniciados = cobroService.reiniciarEstadoMensual();
        log.info("Reinicio mensual de cobros: {} cliente(s) reiniciado(s) a pendiente.", reiniciados);
    }
}
