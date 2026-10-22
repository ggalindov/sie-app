package sie.siejuridicos.documento;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

// Barrido periódico de documentos y firmantes ya sellados/firmados que se quedaron SIN
// sello de tiempo RFC 3161 (la TSA externa no respondió en el momento original -- ese fallo
// nunca bloquea el sellado, solo deja el hueco para que este job lo intente cerrar después).
// Cada item se reintenta en su propia transacción (ver Rfc3161RetryService); un fallo
// individual no afecta a los demás.
@Component
public class Rfc3161RetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(Rfc3161RetryScheduler.class);

    private static final List<String> ESTADOS_SELLADOS = List.of(
            DocumentoFirma.ESTADO_DOC_SELLADO, DocumentoFirma.ESTADO_DOC_REMITIDO, DocumentoFirma.ESTADO_DOC_ENCRIPTADO);

    private final DocumentoFirmaRepository documentoFirmaRepository;
    private final DocumentoFirmanteRepository documentoFirmanteRepository;
    private final Rfc3161RetryService retryService;

    public Rfc3161RetryScheduler(DocumentoFirmaRepository documentoFirmaRepository,
                                  DocumentoFirmanteRepository documentoFirmanteRepository,
                                  Rfc3161RetryService retryService) {
        this.documentoFirmaRepository = documentoFirmaRepository;
        this.documentoFirmanteRepository = documentoFirmanteRepository;
        this.retryService = retryService;
    }

    @Scheduled(initialDelayString = "${app.documento.rfc3161.reintento-initial-delay-ms:300000}",
            fixedDelayString = "${app.documento.rfc3161.reintento-fixed-delay-ms:1800000}")
    public void reintentarPendientes() {
        List<UUID> documentosIds = documentoFirmaRepository.findByRfc3161TokenIsNullAndEstadoDocumentoIn(ESTADOS_SELLADOS)
                .stream().map(DocumentoFirma::getId).toList();
        List<UUID> firmantesIds = documentoFirmanteRepository
                .findByEstadoAndRfc3161TokenIsNullAndHashEvidenciaIsNotNull(DocumentoFirmante.ESTADO_FIRMADO)
                .stream().map(DocumentoFirmante::getId).toList();

        if (documentosIds.isEmpty() && firmantesIds.isEmpty()) {
            return;
        }
        log.info("Reintento RFC3161: {} documento(s) y {} firmante(s) pendientes de sello de tiempo.",
                documentosIds.size(), firmantesIds.size());

        int documentosOk = 0;
        for (UUID id : documentosIds) {
            try {
                if (retryService.reintentarDocumento(id)) {
                    documentosOk++;
                }
            } catch (Exception ex) {
                log.warn("No se pudo reintentar el sello de tiempo del documento {}: {}", id, ex.getMessage());
            }
        }
        int firmantesOk = 0;
        for (UUID id : firmantesIds) {
            try {
                if (retryService.reintentarFirmante(id)) {
                    firmantesOk++;
                }
            } catch (Exception ex) {
                log.warn("No se pudo reintentar el sello de tiempo del firmante {}: {}", id, ex.getMessage());
            }
        }
        log.info("Reintento RFC3161 completado: {}/{} documentos y {}/{} firmantes obtuvieron sello nuevo.",
                documentosOk, documentosIds.size(), firmantesOk, firmantesIds.size());
    }
}
