package sie.siejuridicos.documento;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import sie.siejuridicos.common.exception.ConflictoNegocioException;
import sie.siejuridicos.common.exception.EntidadInvalidaException;
import sie.siejuridicos.documento.dto.DocumentoCambioSolicitudDto;
import sie.siejuridicos.usuario.RolUsuario;
import sie.siejuridicos.usuario.UsuarioInterno;
import sie.siejuridicos.usuario.UsuarioInternoRepository;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// Pedido explícito del usuario de auditar el motor de firma: la doble confirmación
// (ADMIN_GENERAL + ABOGADO) de DocumentChangeRequestService nunca tuvo una prueba
// automatizada propia (solo se había probado manualmente en vivo, una vez, por la UI).
// Esto prueba la garantía de seguridad central: nadie puede aprobar un cambio sensible
// sobre un documento ya sellado por sí solo, ni con dos cuentas del mismo rol.
@ExtendWith(MockitoExtension.class)
class DocumentChangeRequestServiceTest {

    @Mock private DocumentoCambioSolicitudRepository documentoCambioSolicitudRepository;
    @Mock private DocumentoFirmaRepository documentoFirmaRepository;
    @Mock private UsuarioInternoRepository usuarioInternoRepository;
    @Mock private DocumentoFirmanteRepository documentoFirmanteRepository;
    @Mock private DocumentoTimelineService documentoTimelineService;
    @Mock private SignerTokenService signerTokenService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private DocumentChangeRequestService servicio() {
        return new DocumentChangeRequestService(documentoCambioSolicitudRepository, documentoFirmaRepository,
                usuarioInternoRepository, documentoFirmanteRepository, documentoTimelineService, signerTokenService,
                eventPublisher, new ObjectMapper(), "https://siejuridicos.com");
    }

    private static UsuarioInterno usuarioDe(Long id, RolUsuario rol) {
        UsuarioInterno u = new UsuarioInterno();
        u.setId(id);
        u.setNombre("Usuario " + id);
        u.setRol(rol);
        return u;
    }

    private DocumentoFirma documento() {
        return new DocumentoFirma(null, null, "Documento de prueba", "application/pdf", new byte[0]);
    }

    @Test
    void crearDejaLaSolicitudAutoConfirmadaEnElRolDeQuienLaCreo() {
        DocumentoFirma documento = documento();
        UsuarioInterno gerente = usuarioDe(1L, RolUsuario.ADMIN_GENERAL);
        when(documentoFirmaRepository.findById(any())).thenReturn(Optional.of(documento));
        when(usuarioInternoRepository.findById(1L)).thenReturn(Optional.of(gerente));

        DocumentoCambioSolicitudDto dto = servicio().crear(UUID.randomUUID(),
                DocumentoCambioSolicitud.ACCION_INVALIDAR, "Justificación de prueba", null, 1L);

        assertEquals(DocumentoCambioSolicitud.ESTADO_CONFIRMADA_PARCIAL, dto.estado());
    }

    @Test
    void quienCreoLaSolicitudNoPuedeConfirmarElSegundoRolElMismo() {
        DocumentoCambioSolicitud solicitud = solicitudPendienteDe(usuarioDe(1L, RolUsuario.ADMIN_GENERAL));
        UUID id = UUID.randomUUID();
        when(documentoCambioSolicitudRepository.findById(id)).thenReturn(Optional.of(solicitud));
        when(usuarioInternoRepository.findById(1L)).thenReturn(Optional.of(solicitud.getSolicitadoPor()));

        assertThrows(EntidadInvalidaException.class, () -> servicio().confirmar(id, 1L));
    }

    @Test
    void dosConfirmacionesDelMismoRolSeRechazanAunqueSeanStaffDistinto() {
        DocumentoCambioSolicitud solicitud = solicitudPendienteDe(usuarioDe(1L, RolUsuario.ADMIN_GENERAL));
        UsuarioInterno otroGerente = usuarioDe(2L, RolUsuario.ADMIN_GENERAL);
        UUID id = UUID.randomUUID();
        when(documentoCambioSolicitudRepository.findById(id)).thenReturn(Optional.of(solicitud));
        when(usuarioInternoRepository.findById(2L)).thenReturn(Optional.of(otroGerente));

        assertThrows(ConflictoNegocioException.class, () -> servicio().confirmar(id, 2L));
    }

    @Test
    void confirmacionDelRolContrarioApruebaLaSolicitud() {
        DocumentoCambioSolicitud solicitud = solicitudPendienteDe(usuarioDe(1L, RolUsuario.ADMIN_GENERAL));
        UsuarioInterno abogado = usuarioDe(2L, RolUsuario.ABOGADO);
        UUID id = UUID.randomUUID();
        when(documentoCambioSolicitudRepository.findById(id)).thenReturn(Optional.of(solicitud));
        when(usuarioInternoRepository.findById(2L)).thenReturn(Optional.of(abogado));

        DocumentoCambioSolicitudDto dto = servicio().confirmar(id, 2L);

        assertEquals(DocumentoCambioSolicitud.ESTADO_APROBADA, dto.estado());
    }

    @Test
    void ejecutarAntesDeEstarAprobadaSeRechaza() {
        DocumentoCambioSolicitud solicitud = solicitudPendienteDe(usuarioDe(1L, RolUsuario.ADMIN_GENERAL));
        UUID id = UUID.randomUUID();
        when(documentoCambioSolicitudRepository.findById(id)).thenReturn(Optional.of(solicitud));

        assertThrows(ConflictoNegocioException.class, () -> servicio().ejecutar(id, 1L));
    }

    @Test
    void ejecutarUnaInvalidacionYaAprobadaMarcaElDocumentoAlteradoYLaSolicitudEjecutada() {
        DocumentoFirma documento = documento();
        documento.setEstadoIntegridad(DocumentoFirma.INTEGRIDAD_VIGENTE);
        DocumentoCambioSolicitud solicitud = new DocumentoCambioSolicitud(documento, DocumentoCambioSolicitud.ACCION_INVALIDAR,
                "Justificación", null, usuarioDe(1L, RolUsuario.ADMIN_GENERAL));
        solicitud.confirmarComoGerente(usuarioDe(1L, RolUsuario.ADMIN_GENERAL));
        solicitud.confirmarComoAbogado(usuarioDe(2L, RolUsuario.ABOGADO));
        solicitud.setEstado(DocumentoCambioSolicitud.ESTADO_APROBADA);
        UUID id = UUID.randomUUID();
        when(documentoCambioSolicitudRepository.findById(id)).thenReturn(Optional.of(solicitud));
        when(usuarioInternoRepository.findById(2L)).thenReturn(Optional.of(usuarioDe(2L, RolUsuario.ABOGADO)));

        DocumentoCambioSolicitudDto dto = servicio().ejecutar(id, 2L);

        assertEquals(DocumentoFirma.INTEGRIDAD_ALTERADO, documento.getEstadoIntegridad());
        assertEquals(DocumentoCambioSolicitud.ESTADO_EJECUTADA, dto.estado());
    }

    @Test
    void rechazarUnaSolicitudYaEjecutadaSeRechaza() {
        DocumentoCambioSolicitud solicitud = solicitudPendienteDe(usuarioDe(1L, RolUsuario.ADMIN_GENERAL));
        solicitud.marcarEjecutada();
        UUID id = UUID.randomUUID();
        when(documentoCambioSolicitudRepository.findById(id)).thenReturn(Optional.of(solicitud));

        assertThrows(ConflictoNegocioException.class, () -> servicio().rechazar(id, 1L, "motivo"));
    }

    @Test
    void anularFirmaDePateSinFirmanteIdEnElPayloadFalla() {
        DocumentoFirma documento = documento();
        DocumentoCambioSolicitud solicitud = new DocumentoCambioSolicitud(documento, DocumentoCambioSolicitud.ACCION_ANULAR_FIRMA_PARTE,
                "Justificación", null, usuarioDe(1L, RolUsuario.ADMIN_GENERAL));
        solicitud.confirmarComoGerente(usuarioDe(1L, RolUsuario.ADMIN_GENERAL));
        solicitud.confirmarComoAbogado(usuarioDe(2L, RolUsuario.ABOGADO));
        solicitud.setEstado(DocumentoCambioSolicitud.ESTADO_APROBADA);
        UUID id = UUID.randomUUID();
        when(documentoCambioSolicitudRepository.findById(id)).thenReturn(Optional.of(solicitud));

        assertThrows(EntidadInvalidaException.class, () -> servicio().ejecutar(id, 1L));
    }

    private DocumentoCambioSolicitud solicitudPendienteDe(UsuarioInterno solicitante) {
        DocumentoCambioSolicitud solicitud = new DocumentoCambioSolicitud(documento(), DocumentoCambioSolicitud.ACCION_INVALIDAR,
                "Justificación de prueba", null, solicitante);
        if (RolUsuario.ADMIN_GENERAL.equals(solicitante.getRol())) {
            solicitud.confirmarComoGerente(solicitante);
        } else {
            solicitud.confirmarComoAbogado(solicitante);
        }
        solicitud.setEstado(DocumentoCambioSolicitud.ESTADO_CONFIRMADA_PARCIAL);
        return solicitud;
    }
}
