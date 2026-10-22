package sie.siejuridicos.documento;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

// Barrido periódico y PROACTIVO de integridad: una alteración de un documento ya sellado
// (manipulación directa de hash_contenido, contenido_pdf, codigo_verificacion o la cadena
// de eventos) de otro modo solo se detectaría si alguien pide una verificación puntual.
// Este barrido revisa TODOS los documentos sellados y vigentes sin que nadie tenga que
// pedirlo, para que gerencia se entere por la alerta automática aunque nadie consulte
// jamás ese documento en particular. Cada documento se escanea en su propia transacción
// (ver DocumentIntegrityScanService); un fallo individual no afecta al resto del lote.
@Component
public class DocumentIntegrityScanner {

    private static final Logger log = LoggerFactory.getLogger(DocumentIntegrityScanner.class);

    private static final List<String> ESTADOS_SELLADOS = List.of(
            DocumentoFirma.ESTADO_DOC_SELLADO, DocumentoFirma.ESTADO_DOC_REMITIDO, DocumentoFirma.ESTADO_DOC_ENCRIPTADO);

    private final DocumentoFirmaRepository documentoFirmaRepository;
    private final DocumentIntegrityScanService scanService;

    public DocumentIntegrityScanner(DocumentoFirmaRepository documentoFirmaRepository, DocumentIntegrityScanService scanService) {
        this.documentoFirmaRepository = documentoFirmaRepository;
        this.scanService = scanService;
    }

    // Barrido completo: se espacia varias horas (a diferencia de los reintentos de sello
    // RFC 3161, que reintentan condiciones transitorias) porque revisa la totalidad de
    // los documentos sellados vigentes, no un lote pequeño de pendientes.
    @Scheduled(initialDelayString = "${app.documento.integridad.escaneo-initial-delay-ms:900000}",
            fixedDelayString = "${app.documento.integridad.escaneo-fixed-delay-ms:21600000}")
    public void escanear() {
        List<UUID> documentosIds = documentoFirmaRepository.findIdsByEstadoDocumentoInAndEstadoIntegridad(
                ESTADOS_SELLADOS, DocumentoFirma.INTEGRIDAD_VIGENTE);
        if (documentosIds.isEmpty()) {
            return;
        }
        log.info("Barrido de integridad: {} documento(s) sellado(s) y vigente(s) por revisar.", documentosIds.size());

        int alteradosNuevos = 0;
        for (UUID documentoId : documentosIds) {
            try {
                if (scanService.escanearDocumento(documentoId)) {
                    alteradosNuevos++;
                }
            } catch (Exception ex) {
                log.warn("No se pudo escanear la integridad del documento {}: {}", documentoId, ex.getMessage());
            }
        }
        log.info("Barrido de integridad completado: {} documento(s) escaneado(s), {} quedaron ALTERADO en esta corrida.",
                documentosIds.size(), alteradosNuevos);
    }
}
