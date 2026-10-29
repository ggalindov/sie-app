package sie.siejuridicos.documento;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
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
    private final IntegridadBarridoRepository integridadBarridoRepository;

    public DocumentIntegrityScanner(DocumentoFirmaRepository documentoFirmaRepository, DocumentIntegrityScanService scanService,
                                     IntegridadBarridoRepository integridadBarridoRepository) {
        this.documentoFirmaRepository = documentoFirmaRepository;
        this.scanService = scanService;
        this.integridadBarridoRepository = integridadBarridoRepository;
    }

    // Antes cada 6 horas; pedido explícito del usuario de "dejar el motor lo más perfecto":
    // una alteración real queda sin detectar proactivamente hasta 6 horas, cada hora reduce
    // esa ventana de forma sustancial sin ser tan agresivo como para competir por recursos
    // con el resto de la aplicación (el escaneo de cada documento es barato: recalcular
    // hashes, no I/O pesado).
    @Scheduled(initialDelayString = "${app.documento.integridad.escaneo-initial-delay-ms:900000}",
            fixedDelayString = "${app.documento.integridad.escaneo-fixed-delay-ms:3600000}")
    public void escanear() {
        long inicio = System.currentTimeMillis();
        List<UUID> documentosIds = documentoFirmaRepository.findIdsByEstadoDocumentoInAndEstadoIntegridad(
                ESTADOS_SELLADOS, DocumentoFirma.INTEGRIDAD_VIGENTE);

        int alteradosNuevos = 0;
        if (!documentosIds.isEmpty()) {
            log.info("Barrido de integridad: {} documento(s) sellado(s) y vigente(s) por revisar.", documentosIds.size());
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

        // Se registra SIEMPRE, incluso sin documentos por revisar: es la prueba de que el
        // barrido estuvo activo en ese instante, no solo un registro de cuando algo falló
        // (ver V52__integridad_barridos.sql).
        long duracionMs = System.currentTimeMillis() - inicio;
        integridadBarridoRepository.save(
                new IntegridadBarrido(LocalDateTime.now(), documentosIds.size(), alteradosNuevos, duracionMs));
    }
}
