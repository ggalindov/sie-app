package sie.siejuridicos.documento;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Pedido explícito del usuario de auditar el motor de firma: el barrido proactivo de
// integridad (DocumentIntegrityScanService, pensado para correr solo cada 6 horas, ver
// DocumentIntegrityScanner) nunca tuvo una prueba automatizada -- solo se había leído el
// código, nunca se había ejercitado ninguna de sus 4 capas de detección. Cubre cada capa
// por separado y confirma que se detiene en la primera que falla (nunca sigue evaluando
// capas posteriores sobre un documento que ya se marcó ALTERADO).
@ExtendWith(MockitoExtension.class)
class DocumentIntegrityScanServiceTest {

    @Mock private DocumentoFirmaRepository documentoFirmaRepository;
    @Mock private DocumentoEventoChainVerifier documentoEventoChainVerifier;
    @Mock private ApplicationSignatureVerifier applicationSignatureVerifier;
    @Mock private DocumentIntegrityGuard documentIntegrityGuard;
    @Mock private EnvelopeEncryptionService envelopeEncryptionService;
    @Mock private CodigoVerificacionCalculator codigoVerificacionCalculator;

    private DocumentIntegrityScanService servicio() {
        return new DocumentIntegrityScanService(documentoFirmaRepository, documentoEventoChainVerifier,
                applicationSignatureVerifier, documentIntegrityGuard, envelopeEncryptionService, codigoVerificacionCalculator);
    }

    private DocumentoFirma documentoVigente() {
        DocumentoFirma documento = new DocumentoFirma(null, null, "Documento de prueba", "application/pdf", new byte[0]);
        documento.setEstadoIntegridad(DocumentoFirma.INTEGRIDAD_VIGENTE);
        return documento;
    }

    @Test
    void unDocumentoInexistenteNoHaceNada() {
        UUID id = UUID.randomUUID();
        when(documentoFirmaRepository.findById(id)).thenReturn(Optional.empty());

        assertFalse(servicio().escanearDocumento(id));
        verify(documentoEventoChainVerifier, never()).verificar(any());
    }

    @Test
    void unDocumentoYaAlteradoNoVuelveAEscanearse() {
        DocumentoFirma documento = documentoVigente();
        documento.setEstadoIntegridad(DocumentoFirma.INTEGRIDAD_ALTERADO);
        UUID id = UUID.randomUUID();
        when(documentoFirmaRepository.findById(id)).thenReturn(Optional.of(documento));

        assertFalse(servicio().escanearDocumento(id));
        verify(documentoEventoChainVerifier, never()).verificar(any());
    }

    @Test
    void unaCadenaDeEventosRotaDetieneElEscaneoAhi() {
        DocumentoFirma documento = documentoVigente();
        UUID id = UUID.randomUUID();
        when(documentoFirmaRepository.findById(id)).thenReturn(Optional.of(documento));
        when(documentoEventoChainVerifier.verificar(documento)).thenReturn(ResultadoVerificacionCadena.rota(3, "rota"));

        assertTrue(servicio().escanearDocumento(id));
        // El propio DocumentoEventoChainVerifier ya marcó ALTERADO internamente -- este
        // servicio no debe duplicar la llamada a la compuerta.
        verify(documentIntegrityGuard, never()).marcarAlteradoEnNuevaTransaccion(any(), any(), any(), any());
        verify(applicationSignatureVerifier, never()).verificar(any());
    }

    @Test
    void unaFirmaDeAplicacionInvalidaMarcaAlteradoYDetieneElEscaneo() {
        DocumentoFirma documento = documentoVigente();
        UUID id = UUID.randomUUID();
        when(documentoFirmaRepository.findById(id)).thenReturn(Optional.of(documento));
        when(documentoEventoChainVerifier.verificar(documento)).thenReturn(ResultadoVerificacionCadena.cadenaIntegra());
        when(applicationSignatureVerifier.verificar(documento)).thenReturn(false);

        assertTrue(servicio().escanearDocumento(id));
        verify(documentIntegrityGuard).marcarAlteradoEnNuevaTransaccion(any(), any(), eq(DocumentoEvento.ACTOR_SISTEMA), any());
        verify(envelopeEncryptionService, never()).leerContenido(any());
    }

    @Test
    void unHashDeContenidoQueNoCoincideMarcaAlteradoYDetieneElEscaneo() {
        DocumentoFirma documento = documentoVigente();
        documento.setHashContenido("hash-original-esperado");
        UUID id = UUID.randomUUID();
        when(documentoFirmaRepository.findById(id)).thenReturn(Optional.of(documento));
        when(documentoEventoChainVerifier.verificar(documento)).thenReturn(ResultadoVerificacionCadena.cadenaIntegra());
        when(applicationSignatureVerifier.verificar(documento)).thenReturn(true);
        when(envelopeEncryptionService.leerContenido(documento)).thenReturn("contenido manipulado".getBytes());

        assertTrue(servicio().escanearDocumento(id));
        verify(documentIntegrityGuard).marcarAlteradoEnNuevaTransaccion(any(), any(), eq(DocumentoEvento.ACTOR_SISTEMA), any());
        verify(codigoVerificacionCalculator, never()).calcular(any(), anyLong());
    }

    @Test
    void unCodigoDeVerificacionQueNoCoincideMarcaAlterado() {
        DocumentoFirma documento = documentoVigente();
        documento.setCodigoVerificacion("CODIGO-ESPERADO");
        documento.setNumeroSecuencia(42L);
        UUID id = UUID.randomUUID();
        when(documentoFirmaRepository.findById(id)).thenReturn(Optional.of(documento));
        when(documentoEventoChainVerifier.verificar(documento)).thenReturn(ResultadoVerificacionCadena.cadenaIntegra());
        when(applicationSignatureVerifier.verificar(documento)).thenReturn(true);
        when(codigoVerificacionCalculator.calcular(documento.getId(), 42L)).thenReturn("CODIGO-DISTINTO");

        assertTrue(servicio().escanearDocumento(id));
        verify(documentIntegrityGuard).marcarAlteradoEnNuevaTransaccion(any(), any(), eq(DocumentoEvento.ACTOR_SISTEMA), any());
    }

    @Test
    void unDocumentoQuePasaLasCuatroCapasNoSeMarcaAlterado() {
        DocumentoFirma documento = documentoVigente();
        byte[] contenido = "contenido real sin alterar".getBytes();
        // Hash real (no mockeado) del mismo contenido que devuelve envelopeEncryptionService,
        // para probar la comparación real de HashUtil.sha256Hex, no una respuesta fabricada.
        documento.setHashContenido(HashUtil.sha256Hex(contenido));
        documento.setCodigoVerificacion("CODIGO-CORRECTO");
        documento.setNumeroSecuencia(7L);
        UUID id = UUID.randomUUID();
        when(documentoFirmaRepository.findById(id)).thenReturn(Optional.of(documento));
        when(documentoEventoChainVerifier.verificar(documento)).thenReturn(ResultadoVerificacionCadena.cadenaIntegra());
        when(applicationSignatureVerifier.verificar(documento)).thenReturn(true);
        when(envelopeEncryptionService.leerContenido(documento)).thenReturn(contenido);
        when(codigoVerificacionCalculator.calcular(documento.getId(), 7L)).thenReturn("CODIGO-CORRECTO");

        assertFalse(servicio().escanearDocumento(id));
        verify(documentIntegrityGuard, never()).marcarAlteradoEnNuevaTransaccion(any(), any(), any(), any());
    }
}
