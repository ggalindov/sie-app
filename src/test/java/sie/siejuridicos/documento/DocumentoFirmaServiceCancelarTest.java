package sie.siejuridicos.documento;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import sie.siejuridicos.common.exception.EntidadInvalidaException;
import sie.siejuridicos.usuario.UsuarioInterno;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Pedido explícito del usuario: una forma de cancelar un proceso de firma desde el panel
// (gerencia decide no seguir adelante, nadie rechazó firmar) con su PROPIO estado terminal
// CANCELADO -- deliberadamente distinto de ANULADO (esa es la causa automática: un rechazo
// o una expiración, nunca una decisión manual). Estas pruebas fijan ambos comportamientos.
@ExtendWith(MockitoExtension.class)
class DocumentoFirmaServiceCancelarTest {

    @Mock private DocumentoAutoFirmanteStaff documentoAutoFirmanteStaff;
    @Mock private DocumentoTimelineService documentoTimelineService;
    @Mock private DocumentoFirmanteRepository documentoFirmanteRepository;
    @Mock private SignerTokenService signerTokenService;
    @Mock private DocumentoSellaInfoService documentoSellaInfoService;
    @Mock private EvidenceCaptureService evidenceCaptureService;
    @Mock private Rfc3161TimestampClient rfc3161TimestampClient;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private EnvelopeEncryptionService envelopeEncryptionService;

    private DocumentoFirmaService servicio() {
        return new DocumentoFirmaService(documentoAutoFirmanteStaff, documentoTimelineService, documentoFirmanteRepository,
                signerTokenService, documentoSellaInfoService, evidenceCaptureService, rfc3161TimestampClient, eventPublisher,
                "https://siejuridicos.com", envelopeEncryptionService);
    }

    private DocumentoFirma documentoConEstado(String estado) {
        DocumentoFirma documento = new DocumentoFirma(null, null, "Documento de prueba", "application/pdf", new byte[0]);
        try {
            var campoId = DocumentoFirma.class.getDeclaredField("id");
            campoId.setAccessible(true);
            campoId.set(documento, UUID.randomUUID());
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
        documento.setEstadoDocumento(estado);
        return documento;
    }

    private DocumentoFirmante parte(DocumentoFirma documento, int orden, String estado) {
        DocumentoFirmante f = new DocumentoFirmante(documento, DocumentoFirmante.TIPO_PARTE, null, null, "PERSONA_NATURAL",
                "Parte " + orden, "123", orden);
        f.setEstado(estado);
        return f;
    }

    private UsuarioInterno staff() {
        UsuarioInterno u = new UsuarioInterno();
        u.setId(1L);
        u.setNombre("Administradora");
        u.setRol(sie.siejuridicos.usuario.RolUsuario.ADMIN_GENERAL);
        return u;
    }

    @Test
    void cancelarMarcaElDocumentoComoCanceladoNoAnulado() {
        DocumentoFirma documento = documentoConEstado(DocumentoFirma.ESTADO_DOC_EN_FIRMA_PARTES);
        when(documentoFirmanteRepository.findByDocumentoIdOrderByOrdenAsc(documento.getId())).thenReturn(List.of());

        servicio().cancelar(documento, staff(), "Se subió el documento equivocado.");

        assertEquals(DocumentoFirma.ESTADO_DOC_CANCELADO, documento.getEstadoDocumento());
    }

    @Test
    void cancelarRevocaLosEnlacesDeLosFirmantesSinResolver() {
        DocumentoFirma documento = documentoConEstado(DocumentoFirma.ESTADO_DOC_EN_FIRMA_PARTES);
        DocumentoFirmante pendiente = parte(documento, 0, DocumentoFirmante.ESTADO_NOTIFICADO);
        DocumentoFirmante yaFirmo = parte(documento, 1, DocumentoFirmante.ESTADO_FIRMADO);
        when(documentoFirmanteRepository.findByDocumentoIdOrderByOrdenAsc(documento.getId()))
                .thenReturn(List.of(pendiente, yaFirmo));

        servicio().cancelar(documento, staff(), "motivo");

        assertEquals(DocumentoFirmante.ESTADO_REVOCADO, pendiente.getEstado());
        assertEquals(DocumentoFirmante.ESTADO_FIRMADO, yaFirmo.getEstado());
        verify(signerTokenService).revocar(pendiente);
        verify(signerTokenService, never()).revocar(yaFirmo);
    }

    @Test
    void noSePuedeCancelarUnDocumentoYaEncriptado() {
        DocumentoFirma documento = documentoConEstado(DocumentoFirma.ESTADO_DOC_ENCRIPTADO);

        assertThrows(EntidadInvalidaException.class, () -> servicio().cancelar(documento, staff(), "motivo"));
    }

    @Test
    void noSePuedeCancelarUnDocumentoYaCancelado() {
        DocumentoFirma documento = documentoConEstado(DocumentoFirma.ESTADO_DOC_CANCELADO);

        assertThrows(EntidadInvalidaException.class, () -> servicio().cancelar(documento, staff(), "motivo"));
    }

    @Test
    void noSePuedeCancelarUnDocumentoYaAnulado() {
        DocumentoFirma documento = documentoConEstado(DocumentoFirma.ESTADO_DOC_ANULADO);

        assertThrows(EntidadInvalidaException.class, () -> servicio().cancelar(documento, staff(), "motivo"));
    }
}
