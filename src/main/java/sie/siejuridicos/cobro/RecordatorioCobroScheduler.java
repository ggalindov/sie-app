package sie.siejuridicos.cobro;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import sie.siejuridicos.cobro.dto.ResumenEnvioRecordatoriosCobros;

import java.time.LocalDate;

// Pedido explícito del usuario: los recordatorios de cobro corren a partir del día 3 de cada
// mes (en vez del día 1), garantizando que de ninguna manera se crucen ni compitan por el cupo
// diario de 250 mensajes con el reporte periódico de casos (que corre los días 1 y 15).
// Corre a diario a las 8:40 AM (ver app.cobros.recordatorio-cron): el día 3 procesa hasta agotar
// el cupo diario disponible (250). Si queda algún cliente pendiente por límite diario, la corrida
// automática del día 4 a las 8:40 AM reanuda y despacha a los restantes. Una vez notificados todos
// en el mes en curso, los días 5 en adelante simplemente omiten el envío sin generar llamadas innecesarias.
@Component
public class RecordatorioCobroScheduler {

    private static final Logger log = LoggerFactory.getLogger(RecordatorioCobroScheduler.class);

    private final CobroService cobroService;
    private final boolean bloqueoTotalClientes;

    public RecordatorioCobroScheduler(CobroService cobroService,
                                      @Value("${app.bloqueo-total-clientes:true}") boolean bloqueoTotalClientes) {
        this.cobroService = cobroService;
        this.bloqueoTotalClientes = bloqueoTotalClientes;
    }

    @Scheduled(cron = "${app.cobros.recordatorio-cron}")
    public void enviarRecordatoriosDelMes() {
        if (bloqueoTotalClientes) {
            log.info("[SEGURIDAD ACTIVA] RecordatorioCobroScheduler OMITIDO por app.bloqueo-total-clientes=true. Cero mensajes a clientes.");
            return;
        }
        int diaDelMes = LocalDate.now().getDayOfMonth();
        if (diaDelMes < 3 || diaDelMes > 5) {
            log.info("Recordatorio automático de cobro: hoy es día {} del mes. Se omite. "
                    + "El recordatorio programado de cobro solo corre a partir del día 3 (y días 4-5 para despachar remanentes por límite diario).", diaDelMes);
            return;
        }
        ResumenEnvioRecordatoriosCobros resumen = cobroService.enviarRecordatorios();
        log.info("Recordatorio de cobro: {} correo(s), {} WhatsApp, {} cliente(s) sin costo omitido(s), "
                        + "{} pendiente(s) por el límite diario",
                resumen.correosEnviados(), resumen.whatsappEnviados(), resumen.clientesSinCosto(),
                resumen.pendientesPorLimiteDiario());
    }
}
