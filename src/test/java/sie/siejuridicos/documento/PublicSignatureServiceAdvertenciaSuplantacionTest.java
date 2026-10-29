package sie.siejuridicos.documento;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import sie.siejuridicos.common.exception.EntidadInvalidaException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Punto 2 de las mejoras pedidas sobre el motor "hermano" (UCN): el backend debe EXIGIR
// (no solo sugerir) que el firmante haya aceptado la advertencia de suplantación antes de
// aceptar cada una de las dos fotos sensibles -- confirma la compuerta real en servidor,
// no solo la casilla visual del frontend (que se puede saltar llamando la API directo).
class PublicSignatureServiceAdvertenciaSuplantacionTest {

    private static final byte[] PNG_VALIDO = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0,
    };
    private static final String TOKEN = "token-de-prueba";

    private SignerTokenService signerTokenService;
    private EvidenceCaptureService evidenceCaptureService;
    private PublicSignatureService servicio;
    private DocumentoFirmante firmante;

    @BeforeEach
    void preparar() {
        signerTokenService = mock(SignerTokenService.class);
        evidenceCaptureService = mock(EvidenceCaptureService.class);
        OtpService otpService = mock(OtpService.class);
        DocumentoFirmaService documentoFirmaService = mock(DocumentoFirmaService.class);
        EnvelopeEncryptionService envelopeEncryptionService = mock(EnvelopeEncryptionService.class);
        EvidenceCertificateService evidenceCertificateService = mock(EvidenceCertificateService.class);
        DocumentoTimelineService documentoTimelineService = mock(DocumentoTimelineService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

        servicio = new PublicSignatureService(signerTokenService, otpService, evidenceCaptureService,
                documentoFirmaService, envelopeEncryptionService, evidenceCertificateService,
                documentoTimelineService, eventPublisher, 5);

        firmante = new DocumentoFirmante(null, "PARTE", null, null, null, "Juan Pérez", "123456", 0);
        firmante.setEstado(DocumentoFirmante.ESTADO_OTP_VERIFICADO);
        when(signerTokenService.resolver(TOKEN)).thenReturn(firmante);
    }

    @Test
    void rechazaLaFotoDeIdentidadSiNoSeAceptoLaAdvertencia() {
        assertThrows(EntidadInvalidaException.class, () -> servicio.registrarIdentidad(TOKEN, PNG_VALIDO, "image/png"));
        verify(evidenceCaptureService, never()).registrarIdentidad(any(), any(), any());
    }

    @Test
    void permiteLaFotoDeIdentidadUnaVezAceptadaLaAdvertencia() {
        servicio.aceptarAdvertenciaSuplantacionIdentidad(TOKEN, true);
        servicio.registrarIdentidad(TOKEN, PNG_VALIDO, "image/png");
        verify(evidenceCaptureService).registrarIdentidad(firmante, PNG_VALIDO, "image/png");
    }

    @Test
    void rechazaLaFotoDeVerificacionSiNoSeAceptoLaAdvertencia() {
        assertThrows(EntidadInvalidaException.class, () -> servicio.registrarVerificacionHumana(TOKEN, PNG_VALIDO, "image/png"));
        verify(evidenceCaptureService, never()).registrarVerificacionHumana(any(), any(), any());
    }

    @Test
    void permiteLaFotoDeVerificacionUnaVezAceptadaLaAdvertencia() {
        servicio.aceptarAdvertenciaSuplantacionVerificacion(TOKEN, true);
        servicio.registrarVerificacionHumana(TOKEN, PNG_VALIDO, "image/png");
        verify(evidenceCaptureService).registrarVerificacionHumana(firmante, PNG_VALIDO, "image/png");
    }

    @Test
    void noMarcaLaAdvertenciaComoAceptadaSiElFirmanteEnviaAceptarEnFalso() {
        assertThrows(EntidadInvalidaException.class, () -> servicio.aceptarAdvertenciaSuplantacionIdentidad(TOKEN, false));
        assertEquals(null, firmante.getAdvertenciaSuplantacionIdentidadAceptadaEn());
    }
}
