package sie.siejuridicos.documento;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Recordatorios y expiración del proceso de firma de partes (ver FirmaRecordatorioService),
// pedido explícito del usuario de revisar esta pieza -- probada aquí sin esperar horas/días
// reales, manipulando directamente los campos de tiempo del firmante de prueba.
@ExtendWith(MockitoExtension.class)
class FirmaRecordatorioServiceTest {

    @Mock private DocumentoFirmaRepository documentoFirmaRepository;
    @Mock private DocumentoFirmanteRepository documentoFirmanteRepository;
    @Mock private SignerTokenService signerTokenService;
    @Mock private DocumentoTimelineService documentoTimelineService;
    @Mock private ApplicationEventPublisher eventPublisher;

    private FirmaRecordatorioService servicio() {
        return new FirmaRecordatorioService(documentoFirmaRepository, documentoFirmanteRepository, signerTokenService,
                documentoTimelineService, eventPublisher, "https://siejuridicos.com",
                true, 24, 3, 10);
    }

    private DocumentoFirma documentoConId(UUID id, String estado) {
        DocumentoFirma documento = new DocumentoFirma(null, null, "Documento de prueba", "application/pdf", new byte[0]);
        documento.setEstadoDocumento(estado);
        try {
            var campoId = DocumentoFirma.class.getDeclaredField("id");
            campoId.setAccessible(true);
            campoId.set(documento, id);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
        return documento;
    }

    private DocumentoFirmante parte(DocumentoFirma documento, int orden, String estado, LocalDateTime creadoEn) {
        DocumentoFirmante f = new DocumentoFirmante(documento, DocumentoFirmante.TIPO_PARTE, null, null, "PERSONA_NATURAL",
                "Parte " + orden, "123", orden);
        f.setEstado(estado);
        try {
            var campoCreado = DocumentoFirmante.class.getDeclaredField("creadoEn");
            campoCreado.setAccessible(true);
            campoCreado.set(f, creadoEn);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
        return f;
    }

    @Test
    void recuerdaCuandoHanPasadoMasDeVeinticuatroHorasDesdeElEnlace() {
        LocalDateTime ahora = LocalDateTime.now();
        DocumentoFirma documento = documentoConId(UUID.randomUUID(), DocumentoFirma.ESTADO_DOC_EN_FIRMA_PARTES);
        DocumentoFirmante firmante = parte(documento, 0, DocumentoFirmante.ESTADO_NOTIFICADO, ahora.minusHours(25));
        firmante.setTokenHash("hash");
        firmante.setEnlaceEmitidoEn(ahora.minusHours(25));
        when(documentoFirmaRepository.findById(documento.getId())).thenReturn(java.util.Optional.of(documento));
        when(documentoFirmanteRepository.findByDocumentoIdOrderByOrdenAsc(documento.getId())).thenReturn(List.of(firmante));
        when(signerTokenService.esSuTurno(firmante)).thenReturn(true);
        when(signerTokenService.renovarPorRecordatorio(firmante)).thenReturn("token-nuevo");

        boolean expiro = servicio().procesarDocumento(documento.getId(), ahora);

        assertFalse(expiro);
        verify(signerTokenService).renovarPorRecordatorio(firmante);
        verify(documentoTimelineService).registrar(eq(documento), eq(DocumentoEvento.TIPO_RECORDATORIO_ENVIADO), any(), any(),
                eq(firmante), any(), any(), any());
    }

    @Test
    void noRecuerdaSiAunNoHanPasadoVeinticuatroHoras() {
        LocalDateTime ahora = LocalDateTime.now();
        DocumentoFirma documento = documentoConId(UUID.randomUUID(), DocumentoFirma.ESTADO_DOC_EN_FIRMA_PARTES);
        DocumentoFirmante firmante = parte(documento, 0, DocumentoFirmante.ESTADO_NOTIFICADO, ahora.minusHours(1));
        firmante.setTokenHash("hash");
        firmante.setEnlaceEmitidoEn(ahora.minusHours(1));
        when(documentoFirmaRepository.findById(documento.getId())).thenReturn(java.util.Optional.of(documento));
        when(documentoFirmanteRepository.findByDocumentoIdOrderByOrdenAsc(documento.getId())).thenReturn(List.of(firmante));

        servicio().procesarDocumento(documento.getId(), ahora);

        verify(signerTokenService, never()).renovarPorRecordatorio(any());
    }

    @Test
    void noRecuerdaSiYaAlcanzoElMaximoDeRecordatorios() {
        LocalDateTime ahora = LocalDateTime.now();
        DocumentoFirma documento = documentoConId(UUID.randomUUID(), DocumentoFirma.ESTADO_DOC_EN_FIRMA_PARTES);
        DocumentoFirmante firmante = parte(documento, 0, DocumentoFirmante.ESTADO_NOTIFICADO, ahora.minusHours(100));
        firmante.setTokenHash("hash");
        firmante.setEnlaceEmitidoEn(ahora.minusHours(100));
        firmante.registrarRecordatorio(ahora.minusHours(99));
        firmante.registrarRecordatorio(ahora.minusHours(50));
        firmante.registrarRecordatorio(ahora.minusHours(26));
        when(documentoFirmaRepository.findById(documento.getId())).thenReturn(java.util.Optional.of(documento));
        when(documentoFirmanteRepository.findByDocumentoIdOrderByOrdenAsc(documento.getId())).thenReturn(List.of(firmante));

        servicio().procesarDocumento(documento.getId(), ahora);

        verify(signerTokenService, never()).renovarPorRecordatorio(any());
    }

    @Test
    void noRecuerdaSiNoEsSuTurno() {
        LocalDateTime ahora = LocalDateTime.now();
        DocumentoFirma documento = documentoConId(UUID.randomUUID(), DocumentoFirma.ESTADO_DOC_EN_FIRMA_PARTES);
        DocumentoFirmante firmante = parte(documento, 1, DocumentoFirmante.ESTADO_NOTIFICADO, ahora.minusHours(30));
        firmante.setTokenHash("hash");
        firmante.setEnlaceEmitidoEn(ahora.minusHours(30));
        when(documentoFirmaRepository.findById(documento.getId())).thenReturn(java.util.Optional.of(documento));
        when(documentoFirmanteRepository.findByDocumentoIdOrderByOrdenAsc(documento.getId())).thenReturn(List.of(firmante));
        when(signerTokenService.esSuTurno(firmante)).thenReturn(false);

        servicio().procesarDocumento(documento.getId(), ahora);

        verify(signerTokenService, never()).renovarPorRecordatorio(any());
    }

    @Test
    void expiraElDocumentoTrasDiezDiasSinCompletarse() {
        LocalDateTime ahora = LocalDateTime.now();
        DocumentoFirma documento = documentoConId(UUID.randomUUID(), DocumentoFirma.ESTADO_DOC_EN_FIRMA_PARTES);
        DocumentoFirmante sinResolver = parte(documento, 0, DocumentoFirmante.ESTADO_NOTIFICADO, ahora.minusDays(11));
        when(documentoFirmaRepository.findById(documento.getId())).thenReturn(java.util.Optional.of(documento));
        when(documentoFirmanteRepository.findByDocumentoIdOrderByOrdenAsc(documento.getId())).thenReturn(List.of(sinResolver));

        boolean expiro = servicio().procesarDocumento(documento.getId(), ahora);

        assertTrue(expiro);
        assertEquals(DocumentoFirmante.ESTADO_EXPIRADO, sinResolver.getEstado());
        assertEquals(DocumentoFirma.ESTADO_DOC_ANULADO, documento.getEstadoDocumento());
        verify(signerTokenService).revocar(sinResolver);
    }

    @Test
    void noExpiraSiAunNoHaPasadoElPlazoDeDiezDias() {
        LocalDateTime ahora = LocalDateTime.now();
        DocumentoFirma documento = documentoConId(UUID.randomUUID(), DocumentoFirma.ESTADO_DOC_EN_FIRMA_PARTES);
        DocumentoFirmante firmante = parte(documento, 0, DocumentoFirmante.ESTADO_NOTIFICADO, ahora.minusDays(2));
        firmante.setTokenHash("hash");
        firmante.setEnlaceEmitidoEn(ahora.minusHours(1));
        when(documentoFirmaRepository.findById(documento.getId())).thenReturn(java.util.Optional.of(documento));
        when(documentoFirmanteRepository.findByDocumentoIdOrderByOrdenAsc(documento.getId())).thenReturn(List.of(firmante));

        boolean expiro = servicio().procesarDocumento(documento.getId(), ahora);

        assertFalse(expiro);
        assertEquals(DocumentoFirma.ESTADO_DOC_EN_FIRMA_PARTES, documento.getEstadoDocumento());
    }

    @Test
    void noProcesaUnDocumentoQueNoEstaEnFirmaDePartes() {
        DocumentoFirma documento = documentoConId(UUID.randomUUID(), DocumentoFirma.ESTADO_DOC_EMITIDO);
        when(documentoFirmaRepository.findById(documento.getId())).thenReturn(java.util.Optional.of(documento));

        boolean expiro = servicio().procesarDocumento(documento.getId(), LocalDateTime.now());

        assertFalse(expiro);
        verify(documentoFirmanteRepository, never()).findByDocumentoIdOrderByOrdenAsc(any());
    }
}
