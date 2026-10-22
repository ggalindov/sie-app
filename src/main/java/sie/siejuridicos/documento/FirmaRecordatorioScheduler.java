package sie.siejuridicos.documento;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

// Barrido periódico: envía recordatorios al firmante pendiente y expira los procesos de
// firma que superaron su plazo (ver FirmaRecordatorioService). Un fallo en un documento no
// detiene el resto.
@Component
public class FirmaRecordatorioScheduler {

    private static final Logger log = LoggerFactory.getLogger(FirmaRecordatorioScheduler.class);

    private final FirmaRecordatorioService recordatorioService;

    public FirmaRecordatorioScheduler(FirmaRecordatorioService recordatorioService) {
        this.recordatorioService = recordatorioService;
    }

    @Scheduled(initialDelayString = "${app.documento.recordatorios.barrido-initial-delay-ms:600000}",
            fixedDelayString = "${app.documento.recordatorios.barrido-fixed-delay-ms:900000}")
    public void barrer() {
        if (!recordatorioService.estaHabilitado()) {
            return;
        }
        LocalDateTime ahora = LocalDateTime.now();
        int expirados = 0;
        for (UUID documentoId : recordatorioService.documentosPendientes()) {
            try {
                if (recordatorioService.procesarDocumento(documentoId, ahora)) {
                    expirados++;
                }
            } catch (Exception ex) {
                log.warn("No se pudieron procesar los recordatorios del documento {}: {}", documentoId, ex.getMessage());
            }
        }
        if (expirados > 0) {
            log.info("Barrido de recordatorios: {} proceso(s) de firma expirado(s).", expirados);
        }
    }
}
