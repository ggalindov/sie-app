package sie.siejuridicos.cobro;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import sie.siejuridicos.cobro.dto.ResumenEnvioRecordatoriosCobros;

// Mismo patrón que solicitud.RecordatorioCitaScheduler: corre una vez (aquí, una vez al mes,
// el día 1) y delega toda la lógica real en el servicio -- este componente solo decide
// CUÁNDO, nunca CÓMO.
@Component
public class RecordatorioCobroScheduler {

    private static final Logger log = LoggerFactory.getLogger(RecordatorioCobroScheduler.class);

    private final CobroService cobroService;

    public RecordatorioCobroScheduler(CobroService cobroService) {
        this.cobroService = cobroService;
    }

    @Scheduled(cron = "${app.cobros.recordatorio-cron}")
    public void enviarRecordatoriosDelMes() {
        ResumenEnvioRecordatoriosCobros resumen = cobroService.enviarRecordatorios();
        log.info("Recordatorio mensual de cobro: {} correo(s), {} WhatsApp, {} cliente(s) sin costo omitido(s)",
                resumen.correosEnviados(), resumen.whatsappEnviados(), resumen.clientesSinCosto());
    }
}
