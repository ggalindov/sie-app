package sie.siejuridicos.documento;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;
import sie.siejuridicos.documento.events.FirmaStaffActualizadaEvent;

import java.util.List;
import java.util.UUID;

// Cierra la compuerta de firmas de staff (ver DocumentoFirmaService#intentarFirmarYSellar):
// sin este reintento, un documento que quedó EMITIDO porque faltaba la firma de un gerente o
// abogado se quedaría así para siempre. En cuanto CUALQUIER usuario interno carga su firma
// (sin importar si era justo la pieza que faltaba), se recorren todos los documentos que
// siguen EMITIDOS y se vuelve a intentar cerrar su compuerta. A diferencia del motor de
// referencia (que necesita un caso especial para "avisar al solicitante" cuando el sellado
// ocurre aquí y no en la emisión original), en SIE el propio
// DocumentoFirmaService#intentarFirmarYSellar ya notifica a las partes externas (si las
// hay) de la misma forma sin importar cuándo se cierre la compuerta -- no hace falta
// ningún caso especial.
//
// Corre AFTER_COMMIT (la firma recién cargada ya es visible) y cada documento en su propia
// transacción REQUIRES_NEW: un fallo en uno se registra en el log y no revierte ni detiene
// el resto del lote, ni la carga de la firma que lo disparó.
@Component
public class DocumentoFirmaStaffRetryListener {

    private static final Logger log = LoggerFactory.getLogger(DocumentoFirmaStaffRetryListener.class);

    private final DocumentoFirmaRepository documentoFirmaRepository;
    private final DocumentoFirmaService documentoFirmaService;
    private final TransactionTemplate transactionTemplate;

    public DocumentoFirmaStaffRetryListener(DocumentoFirmaRepository documentoFirmaRepository,
                                             DocumentoFirmaService documentoFirmaService,
                                             PlatformTransactionManager transactionManager) {
        this.documentoFirmaRepository = documentoFirmaRepository;
        this.documentoFirmaService = documentoFirmaService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFirmaStaffActualizada(FirmaStaffActualizadaEvent event) {
        reintentarDocumentosPendientesDeFirma();
    }

    // Devuelve cuántos documentos avanzaron más allá de EMITIDO en este reintento.
    public int reintentarDocumentosPendientesDeFirma() {
        List<UUID> pendientes = transactionTemplate.execute(status ->
                documentoFirmaRepository.findIdsByEstadoDocumento(DocumentoFirma.ESTADO_DOC_EMITIDO));
        if (pendientes == null || pendientes.isEmpty()) {
            return 0;
        }
        int avanzaron = 0;
        for (UUID documentoId : pendientes) {
            try {
                if (Boolean.TRUE.equals(transactionTemplate.execute(status -> reintentar(documentoId)))) {
                    avanzaron++;
                }
            } catch (RuntimeException ex) {
                log.error("No se pudo reintentar la firma/sellado del documento {}: {}", documentoId, ex.getMessage(), ex);
            }
        }
        log.info("Reintento de firma de staff: {} de {} documento(s) pendiente(s) avanzaron.", avanzaron, pendientes.size());
        return avanzaron;
    }

    private boolean reintentar(UUID documentoId) {
        DocumentoFirma documento = documentoFirmaRepository.findById(documentoId).orElse(null);
        if (documento == null) {
            return false;
        }
        return documentoFirmaService.intentarFirmarYSellar(documento);
    }
}
