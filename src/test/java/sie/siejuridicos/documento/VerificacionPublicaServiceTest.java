package sie.siejuridicos.documento;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sie.siejuridicos.documento.dto.VerificacionPublicaResponse;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Reporte real del usuario: "si yo subo un documento alterado el sistema automaticamente
// debe detectar y romperlo [su estado], asi no se suba 24/7 debe detectar alteraciones".
// Antes de este fix, verificarPdf() SOLO persistía ALTERADO cuando fallaba la firma de
// aplicación (evaluarRegistro); si el hash SHA-256 del PDF subido coincidía EXACTAMENTE con
// un archivo propio pero su firma PAdES ya no verificaba, el veredicto mostrado en pantalla
// decía ALTERADO pero el registro en base de datos (estadoIntegridad) nunca cambiaba hasta
// que corriera el barrido periódico (DocumentIntegrityScanService, por horario). Lo central
// a probar aquí: ese caso ahora sí persiste de inmediato, vía DocumentIntegrityGuard -- y
// que el caso que el diseño original protege a propósito (un archivo cualquiera, ajeno, que
// no coincide con ningún hash propio) sigue sin persistir, para no dejar que cualquiera
// envenene el estado de un documento auténtico subiendo una copia manipulada.
@ExtendWith(MockitoExtension.class)
class VerificacionPublicaServiceTest {

    @Mock private DocumentoFirmaRepository documentoRepository;
    @Mock private DocumentoVersionRepository versionRepository;
    @Mock private DocumentoFirmanteRepository firmanteRepository;
    @Mock private DocumentoEventoChainVerifier chainVerifier;
    @Mock private ApplicationSignatureVerifier applicationSignatureVerifier;
    @Mock private DocumentIntegrityGuard integrityGuard;
    @Mock private DocumentoTimelineService timelineService;
    @Mock private PadesInspector padesInspector;

    private VerificacionPublicaService servicio() {
        return new VerificacionPublicaService(documentoRepository, versionRepository, firmanteRepository,
                chainVerifier, applicationSignatureVerifier, integrityGuard, timelineService, padesInspector);
    }

    private DocumentoFirma documentoVigente(String hash) {
        DocumentoFirma documento = new DocumentoFirma();
        documento.setNombre("Contrato de prestación de servicios");
        documento.setHashContenido(hash);
        documento.setEstadoDocumento(DocumentoFirma.ESTADO_DOC_ENCRIPTADO);
        documento.setEstadoIntegridad(DocumentoFirma.INTEGRIDAD_VIGENTE);
        return documento;
    }

    @Test
    void hashCoincideConArchivoPropioPeroFirmaPadesInvalida_persisteAlteradoDeInmediato() {
        byte[] pdfSubido = "contenido-pdf-de-prueba".getBytes();
        String hash = HashUtil.sha256Hex(pdfSubido);
        DocumentoFirma documento = documentoVigente(hash);

        when(chainVerifier.verificar(documento)).thenReturn(new ResultadoVerificacionCadena(true, null, null));
        when(applicationSignatureVerifier.verificar(documento)).thenReturn(true);
        when(padesInspector.inspeccionar(pdfSubido))
                .thenReturn(new VerificacionPublicaResponse.FirmaPades(1, false, true, true));
        when(documentoRepository.findFirstByHashContenidoIgnoreCase(hash)).thenReturn(Optional.of(documento));
        when(versionRepository.countByDocumentoId(any())).thenReturn(0L);
        when(firmanteRepository.findByDocumentoIdOrderByOrdenAsc(any())).thenReturn(List.of());

        VerificacionPublicaResponse respuesta = servicio().verificarPdf(pdfSubido, "203.0.113.5");

        assertEquals(VerificacionVeredicto.ALTERADO, respuesta.veredicto());
        verify(integrityGuard).marcarAlterado(eq(documento), anyString(), eq(DocumentoEvento.ACTOR_SISTEMA), eq("203.0.113.5"));
    }

    @Test
    void archivoAjenoSinCoincidenciaDeHashNiFirmaDeSie_noPersisteNada() {
        byte[] pdfAjeno = "archivo-que-nunca-paso-por-sie".getBytes();
        String hash = HashUtil.sha256Hex(pdfAjeno);

        when(padesInspector.inspeccionar(pdfAjeno))
                .thenReturn(new VerificacionPublicaResponse.FirmaPades(0, false, false, true));
        when(documentoRepository.findFirstByHashContenidoIgnoreCase(hash)).thenReturn(Optional.empty());
        when(versionRepository.findFirstByHashContenidoIgnoreCase(hash)).thenReturn(Optional.empty());

        VerificacionPublicaResponse respuesta = servicio().verificarPdf(pdfAjeno, "203.0.113.5");

        assertEquals(VerificacionVeredicto.NO_REGISTRADO, respuesta.veredicto());
        verify(integrityGuard, never()).marcarAlterado(any(), anyString(), anyString(), any());
    }
}
