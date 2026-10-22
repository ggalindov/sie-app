package sie.siejuridicos.documento;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import sie.siejuridicos.documento.events.DocumentoFirmaRechazadaEvent;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Auditoría de seguridad (hallazgo real de esta sesión): antes de este cambio, un firmante
// RECHAZADO contaba como "firma completa" para la compuerta de sellado
// (DocumentoSellaInfoService#intentarSellarYRemitir), así que un documento donde una parte
// se negó a firmar podía terminar sellado, firmado digitalmente y remitido a todas las
// partes como si todas hubieran estado de acuerdo. Estas pruebas fijan el comportamiento
// correcto: un rechazo ANULA el documento entero de inmediato y nunca llega a sellarse.
@ExtendWith(MockitoExtension.class)
class DocumentoFirmaServiceRechazoTest {

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

    private DocumentoFirma documentoConId(UUID id) {
        DocumentoFirma documento = new DocumentoFirma(null, null, "Documento de prueba", "application/pdf", new byte[0]);
        try {
            var campoId = DocumentoFirma.class.getDeclaredField("id");
            campoId.setAccessible(true);
            campoId.set(documento, id);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
        return documento;
    }

    private DocumentoFirmante parte(DocumentoFirma documento, int orden, String estado) {
        DocumentoFirmante f = new DocumentoFirmante(documento, DocumentoFirmante.TIPO_PARTE, null, null, "PERSONA_NATURAL",
                "Parte " + orden, "123", orden);
        f.setEstado(estado);
        // El id real lo asigna Hibernate al persistir; se fija por reflexión para que
        // registrarRechazoDeParte() pueda comparar getId() entre firmantes de prueba.
        try {
            var campoId = DocumentoFirmante.class.getDeclaredField("id");
            campoId.setAccessible(true);
            campoId.set(f, UUID.randomUUID());
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
        return f;
    }

    @Test
    void rechazarAnulaElDocumentoYNuncaIntentaSellar() {
        DocumentoFirma documento = documentoConId(UUID.randomUUID());
        DocumentoFirmante queRechaza = parte(documento, 0, DocumentoFirmante.ESTADO_OTP_VERIFICADO);
        when(documentoFirmanteRepository.findByDocumentoIdOrderByOrdenAsc(documento.getId())).thenReturn(List.of(queRechaza));

        servicio().registrarRechazoDeParte(queRechaza, "No estoy de acuerdo con el contenido.", "127.0.0.1", "agente");

        assertEquals(DocumentoFirmante.ESTADO_RECHAZADO, queRechaza.getEstado());
        assertEquals(DocumentoFirma.ESTADO_DOC_ANULADO, documento.getEstadoDocumento());
        verify(documentoSellaInfoService, never()).intentarSellarYRemitir(any());
        verify(eventPublisher).publishEvent(any(DocumentoFirmaRechazadaEvent.class));
    }

    @Test
    void rechazarRevocaElEnlaceDeLasPartesQueAunNoHabianFirmado() {
        DocumentoFirma documento = documentoConId(UUID.randomUUID());
        DocumentoFirmante primera = parte(documento, 0, DocumentoFirmante.ESTADO_OTP_VERIFICADO);
        DocumentoFirmante segunda = parte(documento, 1, DocumentoFirmante.ESTADO_PENDIENTE);
        when(documentoFirmanteRepository.findByDocumentoIdOrderByOrdenAsc(documento.getId())).thenReturn(List.of(primera, segunda));

        servicio().registrarRechazoDeParte(primera, null, "127.0.0.1", "agente");

        assertEquals(DocumentoFirmante.ESTADO_REVOCADO, segunda.getEstado());
        verify(signerTokenService).revocar(segunda);
    }

    @Test
    void rechazarNoTocaAQuienYaHabiaFirmado() {
        DocumentoFirma documento = documentoConId(UUID.randomUUID());
        DocumentoFirmante yaFirmo = parte(documento, 0, DocumentoFirmante.ESTADO_FIRMADO);
        DocumentoFirmante rechaza = parte(documento, 1, DocumentoFirmante.ESTADO_OTP_VERIFICADO);
        when(documentoFirmanteRepository.findByDocumentoIdOrderByOrdenAsc(documento.getId())).thenReturn(List.of(yaFirmo, rechaza));

        servicio().registrarRechazoDeParte(rechaza, "motivo", "127.0.0.1", "agente");

        assertEquals(DocumentoFirmante.ESTADO_FIRMADO, yaFirmo.getEstado());
        verify(signerTokenService, never()).revocar(yaFirmo);
    }

    @Test
    void laCompuertaDeSelladoNuncaSellaUnDocumentoConUnaParteRechazada() {
        // DocumentoSellaInfoService#intentarSellarYRemitir: aunque alguien lo invoque
        // directamente (sin pasar por registrarRechazoDeParte), debe negarse a sellar --
        // el hallazgo real de esta auditoría era justo que esta compuerta trataba
        // RECHAZADO igual que FIRMADO y sellaba de todas formas.
        DocumentoFirma documento = documentoConId(UUID.randomUUID());
        byte[] contenidoOriginal = "PDF-sin-sellar".getBytes();
        documento.setContenidoPdf(contenidoOriginal);
        documento.setEstadoDocumento(DocumentoFirma.ESTADO_DOC_EN_FIRMA_PARTES);
        DocumentoFirmante firmado = parte(documento, 0, DocumentoFirmante.ESTADO_FIRMADO);
        DocumentoFirmante rechazado = parte(documento, 1, DocumentoFirmante.ESTADO_RECHAZADO);
        when(documentoFirmanteRepository.findByDocumentoIdOrderByOrdenAsc(documento.getId()))
                .thenReturn(List.of(firmado, rechazado));

        new DocumentoSellaInfoService(documentoFirmanteRepository, documentoTimelineService, null, null, null, null,
                null, eventPublisher, null, "https://siejuridicos.com").intentarSellarYRemitir(documento);

        // Nada cambió: ni se estampó, ni se firmó digitalmente, ni se cambió el estado.
        assertEquals(DocumentoFirma.ESTADO_DOC_EN_FIRMA_PARTES, documento.getEstadoDocumento());
        assertEquals(contenidoOriginal, documento.getContenidoPdf());
    }
}
