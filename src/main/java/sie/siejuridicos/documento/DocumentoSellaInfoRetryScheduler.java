package sie.siejuridicos.documento;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

// Reintenta el sellado de documentos donde todas las firmas ya quedaron registradas pero el
// paso de sellado falló la primera vez (ver el comentario de
// DocumentoSellaInfoService#intentarSellarYRemitir y
// DocumentoFirmaService#intentarSellarSinPropagarError -- ese método ya evita que el fallo
// revierta las firmas, este scheduler es lo que completa el ciclo: sin él, un documento que
// falló al sellar se quedaría FIRMADO_STAFF/EN_FIRMA_PARTES para siempre).
//
// Mismo patrón ya probado en este módulo: cada documento se reintenta en su propia
// transacción (REQUIRES_NEW dentro de intentarSellarYRemitir), un fallo individual no afecta
// a los demás ni detiene el lote (ver Rfc3161RetryScheduler, DocumentoFirmaStaffRetryListener).
@Component
public class DocumentoSellaInfoRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(DocumentoSellaInfoRetryScheduler.class);

    // Cualquier estado posterior a FIRMADO_STAFF y anterior a SELLADO: intentarSellarYRemitir
    // ya es seguro de llamar sin importar si en realidad faltan partes por firmar (se sale sin
    // hacer nada, ver hayPendientes), así que no hace falta filtrar más fino aquí.
    private static final List<String> ESTADOS_CANDIDATOS = List.of(
            DocumentoFirma.ESTADO_DOC_FIRMADO_STAFF,
            DocumentoFirma.ESTADO_DOC_NOTIFICADO,
            DocumentoFirma.ESTADO_DOC_EN_FIRMA_PARTES);

    private final DocumentoFirmaRepository documentoFirmaRepository;
    private final DocumentoSellaInfoService documentoSellaInfoService;

    public DocumentoSellaInfoRetryScheduler(DocumentoFirmaRepository documentoFirmaRepository,
                                             DocumentoSellaInfoService documentoSellaInfoService) {
        this.documentoFirmaRepository = documentoFirmaRepository;
        this.documentoSellaInfoService = documentoSellaInfoService;
    }

    @Scheduled(initialDelayString = "${app.documento.sellado.reintento-initial-delay-ms:120000}",
            fixedDelayString = "${app.documento.sellado.reintento-fixed-delay-ms:300000}")
    public void reintentarPendientes() {
        List<UUID> ids = documentoFirmaRepository.findIdsByEstadoDocumentoIn(ESTADOS_CANDIDATOS);
        if (ids.isEmpty()) {
            return;
        }
        log.info("Reintento de sellado: {} documento(s) en estado intermedio, se vuelve a intentar.", ids.size());
        int avanzados = 0;
        for (UUID id : ids) {
            try {
                DocumentoFirma documento = documentoFirmaRepository.findById(id).orElse(null);
                if (documento == null) {
                    continue;
                }
                String estadoAntes = documento.getEstadoDocumento();
                documentoSellaInfoService.intentarSellarYRemitir(documento);
                DocumentoFirma actualizado = documentoFirmaRepository.findById(id).orElse(null);
                if (actualizado != null && !estadoAntes.equals(actualizado.getEstadoDocumento())) {
                    avanzados++;
                }
            } catch (RuntimeException ex) {
                log.warn("Reintento de sellado: el documento {} sigue sin poder sellarse: {}", id, ex.getMessage());
            }
        }
        log.info("Reintento de sellado completado: {} de {} documento(s) avanzaron de estado.", avanzados, ids.size());
    }
}
