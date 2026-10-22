package sie.siejuridicos.documento;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sie.siejuridicos.documento.events.DocumentoAlteradoEvent;

// Endurecimiento pedido explícitamente por el usuario ("auditalo buscando fallas... tiene
// que ser perfecto"): apenas CUALQUIER verificación (pública o interna) detecta que el
// documento no coincide con su hash/código original, este servicio lo marca ALTERADO de
// forma permanente -visible en cualquier consulta futura, incluido el panel- y queda así
// hasta que una DocumentoCambioSolicitud tipo REVERTIR_ALTERADO sea ejecutada.
@Service
public class DocumentIntegrityGuard {

    private static final Logger log = LoggerFactory.getLogger(DocumentIntegrityGuard.class);

    private final DocumentoFirmaRepository documentoFirmaRepository;
    private final DocumentoTimelineService documentoTimelineService;
    private final ApplicationEventPublisher eventPublisher;

    public DocumentIntegrityGuard(DocumentoFirmaRepository documentoFirmaRepository,
                                   DocumentoTimelineService documentoTimelineService,
                                   ApplicationEventPublisher eventPublisher) {
        this.documentoFirmaRepository = documentoFirmaRepository;
        this.documentoTimelineService = documentoTimelineService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void registrarConsulta(DocumentoFirma documento) {
        documentoTimelineService.registrarSistema(documento, DocumentoEvento.TIPO_VERIFICACION_CONSULTADA,
                "Se consultó la verificación pública/interna de este documento.");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void marcarAlterado(DocumentoFirma documento, String motivo, String actorTipo, String ip) {
        marcarAlteradoInterno(documento, motivo, actorTipo, ip);
    }

    // Igual que marcarAlterado, pero en su propia transacción (REQUIRES_NEW): pensado para
    // llamarse desde un contexto de solo lectura, donde la transacción del llamador no
    // admite escrituras.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void marcarAlteradoEnNuevaTransaccion(DocumentoFirma documento, String motivo, String actorTipo, String ip) {
        marcarAlteradoInterno(documento, motivo, actorTipo, ip);
    }

    private void marcarAlteradoInterno(DocumentoFirma documento, String motivo, String actorTipo, String ip) {
        if (DocumentoFirma.INTEGRIDAD_ALTERADO.equals(documento.getEstadoIntegridad())) {
            return;
        }
        documento.setEstadoIntegridad(DocumentoFirma.INTEGRIDAD_ALTERADO);
        // Se usa el resultado de save() (no 'documento') para el evento: en el camino
        // REQUIRES_NEW, 'documento' puede venir de una sesión de Hibernate SUSPENDIDA (la
        // del llamador), y sus relaciones LAZY fallarían si algún listener intenta
        // leerlas. El resultado de save()/merge() siempre queda adjunto a la sesión ACTIVA
        // de esta transacción.
        DocumentoFirma guardado = documentoFirmaRepository.save(documento);
        documentoTimelineService.registrar(documento, DocumentoEvento.TIPO_ALTERADO_DETECTADO, actorTipo, null, null, motivo, ip, null);
        log.warn("Documento {} marcado ALTERADO: {}", documento.getId(), motivo);
        eventPublisher.publishEvent(new DocumentoAlteradoEvent(guardado, motivo));
    }
}
