package sie.siejuridicos.documento;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sie.siejuridicos.common.exception.ConflictoNegocioException;
import sie.siejuridicos.common.exception.EntidadInvalidaException;
import sie.siejuridicos.common.exception.RecursoNoEncontradoException;
import sie.siejuridicos.documento.dto.DocumentoCambioSolicitudDto;
import sie.siejuridicos.documento.events.DocumentoListoParaFirmarEvent;
import sie.siejuridicos.usuario.RolUsuario;
import sie.siejuridicos.usuario.UsuarioInterno;
import sie.siejuridicos.usuario.UsuarioInternoRepository;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

// Doble confirmación (ADMIN_GENERAL + ABOGADO) para cualquier movimiento sensible sobre un
// documento ya emitido: nadie puede aplicar por sí solo un cambio sobre un documento del
// motor de firma, ni siquiera gerencia -- siempre se necesita un segundo staff, con el rol
// contrario, que confirme antes de poder ejecutarlo. Quien crea la solicitud queda
// auto-confirmado en su propio rol (CONFIRMADA_PARCIAL); el segundo confirma con
// confirmar() (APROBADA); solo entonces ejecutar() aplica el cambio real.
@Service
public class DocumentChangeRequestService {

    private final DocumentoCambioSolicitudRepository documentoCambioSolicitudRepository;
    private final DocumentoFirmaRepository documentoFirmaRepository;
    private final UsuarioInternoRepository usuarioInternoRepository;
    private final DocumentoFirmanteRepository documentoFirmanteRepository;
    private final DocumentoTimelineService documentoTimelineService;
    private final SignerTokenService signerTokenService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final String sitioPublicoUrl;

    public DocumentChangeRequestService(DocumentoCambioSolicitudRepository documentoCambioSolicitudRepository,
                                         DocumentoFirmaRepository documentoFirmaRepository,
                                         UsuarioInternoRepository usuarioInternoRepository,
                                         DocumentoFirmanteRepository documentoFirmanteRepository,
                                         DocumentoTimelineService documentoTimelineService,
                                         SignerTokenService signerTokenService,
                                         ApplicationEventPublisher eventPublisher,
                                         ObjectMapper objectMapper,
                                         @Value("${app.documento.sitio-web}") String sitioPublicoUrl) {
        this.documentoCambioSolicitudRepository = documentoCambioSolicitudRepository;
        this.documentoFirmaRepository = documentoFirmaRepository;
        this.usuarioInternoRepository = usuarioInternoRepository;
        this.documentoFirmanteRepository = documentoFirmanteRepository;
        this.documentoTimelineService = documentoTimelineService;
        this.signerTokenService = signerTokenService;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.sitioPublicoUrl = sitioPublicoUrl;
    }

    // IMPORTANTE: cada método público convierte a DTO ANTES de retornar, dentro de su
    // propia transacción -- 'solicitadoPor', 'confirmacionGerente', 'confirmacionAbogado'
    // y 'documento' son relaciones LAZY, y el proyecto corre con
    // spring.jpa.open-in-view=false, así que resolverlas en el controller (fuera de la
    // transacción) lanzaría LazyInitializationException apenas se cerrara la sesión de
    // Hibernate al retornar.
    @Transactional
    public DocumentoCambioSolicitudDto crear(UUID documentoId, String tipoAccion, String justificacion, String payloadJson, Long solicitanteId) {
        DocumentoFirma documento = documentoFirmaRepository.findById(documentoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró el documento solicitado."));
        UsuarioInterno solicitante = usuarioInternoRepository.findById(solicitanteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró el usuario autenticado."));

        DocumentoCambioSolicitud solicitud = new DocumentoCambioSolicitud(documento, tipoAccion, justificacion, payloadJson, solicitante);
        confirmarSegunRol(solicitud, solicitante);
        solicitud.setEstado(DocumentoCambioSolicitud.ESTADO_CONFIRMADA_PARCIAL);
        documentoCambioSolicitudRepository.save(solicitud);

        documentoTimelineService.registrarStaff(documento, DocumentoEvento.TIPO_SOLICITUD_CAMBIO_CREADA, solicitante,
                "Solicitud de '" + tipoAccion + "' creada por " + solicitante.getNombre() + ": " + justificacion);
        return DocumentoCambioSolicitudDto.from(solicitud);
    }

    @Transactional
    public DocumentoCambioSolicitudDto confirmar(UUID solicitudId, Long staffId) {
        DocumentoCambioSolicitud solicitud = obtener(solicitudId);
        if (!DocumentoCambioSolicitud.ESTADO_CONFIRMADA_PARCIAL.equals(solicitud.getEstado())) {
            throw new ConflictoNegocioException("Esta solicitud ya no está pendiente de una segunda confirmación.");
        }
        UsuarioInterno staff = usuarioInternoRepository.findById(staffId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró el usuario autenticado."));

        if (staff.getId().equals(solicitud.getSolicitadoPor().getId())) {
            throw new EntidadInvalidaException("Quien crea la solicitud no puede confirmar el segundo rol él mismo.");
        }
        if (RolUsuario.ADMIN_GENERAL.equals(staff.getRol()) && solicitud.getConfirmacionGerente() != null) {
            throw new ConflictoNegocioException("Esta solicitud ya fue confirmada por un administrador general.");
        }
        if (RolUsuario.ABOGADO.equals(staff.getRol()) && solicitud.getConfirmacionAbogado() != null) {
            throw new ConflictoNegocioException("Esta solicitud ya fue confirmada por un abogado.");
        }

        confirmarSegunRol(solicitud, staff);
        if (solicitud.confirmadaPorAmbosRoles()) {
            solicitud.setEstado(DocumentoCambioSolicitud.ESTADO_APROBADA);
        }
        documentoCambioSolicitudRepository.save(solicitud);
        return DocumentoCambioSolicitudDto.from(solicitud);
    }

    @Transactional
    public DocumentoCambioSolicitudDto rechazar(UUID solicitudId, Long staffId, String motivo) {
        DocumentoCambioSolicitud solicitud = obtener(solicitudId);
        if (DocumentoCambioSolicitud.ESTADO_EJECUTADA.equals(solicitud.getEstado())
                || DocumentoCambioSolicitud.ESTADO_RECHAZADA.equals(solicitud.getEstado())) {
            throw new ConflictoNegocioException("Esta solicitud ya se resolvió y no se puede rechazar.");
        }
        UsuarioInterno staff = usuarioInternoRepository.findById(staffId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró el usuario autenticado."));
        solicitud.rechazar(staff, motivo);
        documentoCambioSolicitudRepository.save(solicitud);
        return DocumentoCambioSolicitudDto.from(solicitud);
    }

    @Transactional
    public DocumentoCambioSolicitudDto ejecutar(UUID solicitudId, Long staffId) {
        DocumentoCambioSolicitud solicitud = obtener(solicitudId);
        if (!DocumentoCambioSolicitud.ESTADO_APROBADA.equals(solicitud.getEstado())) {
            throw new ConflictoNegocioException("La solicitud aún no fue confirmada por un administrador general y un abogado.");
        }
        DocumentoFirma documento = solicitud.getDocumento();
        aplicarAccion(solicitud, documento);
        documentoFirmaRepository.save(documento);
        solicitud.marcarEjecutada();
        documentoCambioSolicitudRepository.save(solicitud);

        documentoTimelineService.registrarStaff(documento, DocumentoEvento.TIPO_SOLICITUD_CAMBIO_EJECUTADA,
                usuarioInternoRepository.findById(staffId).orElse(null),
                "Se ejecutó la solicitud de '" + solicitud.getTipoAccion() + "'.");
        return DocumentoCambioSolicitudDto.from(solicitud);
    }

    @Transactional(readOnly = true)
    public List<DocumentoCambioSolicitudDto> listarPorEstado(String estado) {
        List<DocumentoCambioSolicitud> solicitudes = estado == null || estado.isBlank()
                ? documentoCambioSolicitudRepository.findAll()
                : documentoCambioSolicitudRepository.findByEstadoOrderBySolicitadoEnDesc(estado);
        return solicitudes.stream().map(DocumentoCambioSolicitudDto::from).toList();
    }

    private void aplicarAccion(DocumentoCambioSolicitud solicitud, DocumentoFirma documento) {
        switch (solicitud.getTipoAccion()) {
            case DocumentoCambioSolicitud.ACCION_REVERTIR_ALTERADO -> documento.setEstadoIntegridad(DocumentoFirma.INTEGRIDAD_VIGENTE);
            case DocumentoCambioSolicitud.ACCION_INVALIDAR -> documento.setEstadoIntegridad(DocumentoFirma.INTEGRIDAD_ALTERADO);
            case DocumentoCambioSolicitud.ACCION_RE_EMITIR -> documento.setEstadoDocumento(DocumentoFirma.ESTADO_DOC_EMITIDO);
            case DocumentoCambioSolicitud.ACCION_CORREGIR_METADATO -> aplicarCorreccionMetadato(solicitud, documento);
            case DocumentoCambioSolicitud.ACCION_ANULAR_FIRMA_PARTE -> anularFirmaDeParte(solicitud);
            default -> throw new EntidadInvalidaException("Tipo de acción no reconocido: " + solicitud.getTipoAccion());
        }
    }

    private void aplicarCorreccionMetadato(DocumentoCambioSolicitud solicitud, DocumentoFirma documento) {
        Map<String, String> payload = leerPayload(solicitud);
        String nuevoNombre = payload.get("nombre");
        if (nuevoNombre != null && !nuevoNombre.isBlank()) {
            documento.setNombre(nuevoNombre);
        }
    }

    private void anularFirmaDeParte(DocumentoCambioSolicitud solicitud) {
        Map<String, String> payload = leerPayload(solicitud);
        String firmanteIdTexto = payload.get("firmanteId");
        if (firmanteIdTexto == null) {
            throw new EntidadInvalidaException("La solicitud debe indicar 'firmanteId' en su payload.");
        }
        DocumentoFirmante firmante = documentoFirmanteRepository.findById(UUID.fromString(firmanteIdTexto))
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró el firmante indicado."));
        firmante.setEstado(DocumentoFirmante.ESTADO_PENDIENTE);
        String tokenPlano = signerTokenService.emitirToken(firmante);
        firmante.setEstado(DocumentoFirmante.ESTADO_NOTIFICADO);
        documentoFirmanteRepository.save(firmante);
        eventPublisher.publishEvent(new DocumentoListoParaFirmarEvent(firmante, sitioPublicoUrl + "/firmar/" + tokenPlano));
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> leerPayload(DocumentoCambioSolicitud solicitud) {
        if (solicitud.getPayloadJson() == null || solicitud.getPayloadJson().isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(solicitud.getPayloadJson(), Map.class);
        } catch (Exception ex) {
            throw new EntidadInvalidaException("El payload de la solicitud no es un JSON válido.");
        }
    }

    private void confirmarSegunRol(DocumentoCambioSolicitud solicitud, UsuarioInterno staff) {
        if (RolUsuario.ADMIN_GENERAL.equals(staff.getRol())) {
            solicitud.confirmarComoGerente(staff);
        } else {
            solicitud.confirmarComoAbogado(staff);
        }
    }

    private DocumentoCambioSolicitud obtener(UUID id) {
        return documentoCambioSolicitudRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró la solicitud de cambio indicada."));
    }
}
