package sie.siejuridicos.documento;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Base64;
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

// Pedido explícito del usuario de auditar el motor de firma: DocumentoEventoChainVerifier
// nunca tuvo una sola prueba automatizada, pese a ser la pieza que sostiene el valor
// probatorio de toda la cadena de custodia (ver su comentario de clase). Construye una
// cadena real con HashChainCalculator (misma clase de producción, sin mocks de
// criptografía) y confirma tanto el camino íntegro como cada forma de ruptura que el
// verificador distingue: salto de secuencia, hash-anterior desajustado, y contenido de un
// evento alterado después de calculado su hash.
@ExtendWith(MockitoExtension.class)
class DocumentoEventoChainVerifierTest {

    private static final String CLAVE_MAESTRA = Base64.getEncoder().encodeToString(new byte[32]);

    @Mock private DocumentoEventoRepository documentoEventoRepository;
    @Mock private DocumentoEventoCheckpointRepository checkpointRepository;
    @Mock private DocumentIntegrityGuard documentIntegrityGuard;

    private final HashChainCalculator hashChainCalculator =
            new HashChainCalculator(new DocumentoClaveMaestraService(CLAVE_MAESTRA));

    private DocumentoEventoChainVerifier verificador() {
        return new DocumentoEventoChainVerifier(documentoEventoRepository, checkpointRepository, hashChainCalculator, documentIntegrityGuard);
    }

    private DocumentoFirma documentoConId(UUID id) {
        DocumentoFirma documento = new DocumentoFirma(null, null, "Documento de prueba", "application/pdf", new byte[0]);
        try {
            Field campoId = DocumentoFirma.class.getDeclaredField("id");
            campoId.setAccessible(true);
            campoId.set(documento, id);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
        return documento;
    }

    private DocumentoEvento eventoEncadenado(DocumentoFirma documento, long secuencia, String hashAnterior, String tipo) {
        LocalDateTime ocurrido = LocalDateTime.of(2026, 1, 1, 10, 0).plusMinutes(secuencia);
        String hash = hashChainCalculator.calcular(documento.getId(), secuencia, hashAnterior, tipo,
                DocumentoEvento.ACTOR_SISTEMA, null, null, "evento " + secuencia, "127.0.0.1", "agente", null, ocurrido);
        return new DocumentoEvento(documento, tipo, DocumentoEvento.ACTOR_SISTEMA, null, null, "evento " + secuencia,
                "127.0.0.1", "agente", null, secuencia, hashAnterior, hash, ocurrido);
    }

    @Test
    void cadenaIntegraDeVariosEventosSeVerificaComoValida() {
        DocumentoFirma documento = documentoConId(UUID.randomUUID());
        DocumentoEvento e1 = eventoEncadenado(documento, 1, HashChainCalculator.GENESIS, DocumentoEvento.TIPO_DILIGENCIADO);
        DocumentoEvento e2 = eventoEncadenado(documento, 2, e1.getHashEvento(), DocumentoEvento.TIPO_EMITIDO);
        DocumentoEvento e3 = eventoEncadenado(documento, 3, e2.getHashEvento(), DocumentoEvento.TIPO_SELLADO);
        when(documentoEventoRepository.findByDocumentoIdOrderBySecuenciaAsc(documento.getId())).thenReturn(List.of(e1, e2, e3));

        ResultadoVerificacionCadena resultado = verificador().verificar(documento);

        assertTrue(resultado.integra());
        verify(documentIntegrityGuard, never()).marcarAlteradoEnNuevaTransaccion(any(), any(), any(), any());
    }

    @Test
    void sinEventosQuedaPendienteSinMarcarAlterado() {
        DocumentoFirma documento = documentoConId(UUID.randomUUID());
        when(documentoEventoRepository.findByDocumentoIdOrderBySecuenciaAsc(documento.getId())).thenReturn(List.of());

        ResultadoVerificacionCadena resultado = verificador().verificar(documento);

        assertTrue(resultado.integra());
        assertEquals(null, resultado.secuenciaRota());
        verify(documentIntegrityGuard, never()).marcarAlteradoEnNuevaTransaccion(any(), any(), any(), any());
    }

    @Test
    void unSaltoEnLaSecuenciaSeDetectaYMarcaAlterado() {
        DocumentoFirma documento = documentoConId(UUID.randomUUID());
        DocumentoEvento e1 = eventoEncadenado(documento, 1, HashChainCalculator.GENESIS, DocumentoEvento.TIPO_DILIGENCIADO);
        // Secuencia 2 borrada/omitida: el siguiente evento quedó con secuencia 3.
        DocumentoEvento e3 = eventoEncadenado(documento, 3, e1.getHashEvento(), DocumentoEvento.TIPO_SELLADO);
        when(documentoEventoRepository.findByDocumentoIdOrderBySecuenciaAsc(documento.getId())).thenReturn(List.of(e1, e3));

        ResultadoVerificacionCadena resultado = verificador().verificar(documento);

        assertFalse(resultado.integra());
        assertEquals(3L, resultado.secuenciaRota());
        verify(documentIntegrityGuard).marcarAlteradoEnNuevaTransaccion(eq(documento), any(), eq(DocumentoEvento.ACTOR_SISTEMA), eq(null));
    }

    @Test
    void unHashAnteriorQueNoEncajaSeDetectaYMarcaAlterado() {
        DocumentoFirma documento = documentoConId(UUID.randomUUID());
        DocumentoEvento e1 = eventoEncadenado(documento, 1, HashChainCalculator.GENESIS, DocumentoEvento.TIPO_DILIGENCIADO);
        // e2 no encadena con el hash real de e1 (como si su fila hubiera sido reemplazada).
        DocumentoEvento e2 = eventoEncadenado(documento, 2, "hash-falso-no-es-el-de-e1", DocumentoEvento.TIPO_EMITIDO);
        when(documentoEventoRepository.findByDocumentoIdOrderBySecuenciaAsc(documento.getId())).thenReturn(List.of(e1, e2));

        ResultadoVerificacionCadena resultado = verificador().verificar(documento);

        assertFalse(resultado.integra());
        assertEquals(2L, resultado.secuenciaRota());
        verify(documentIntegrityGuard).marcarAlteradoEnNuevaTransaccion(eq(documento), any(), eq(DocumentoEvento.ACTOR_SISTEMA), eq(null));
    }

    @Test
    void unContenidoDeEventoAlteradoDespuesDeCalcularSuHashSeDetecta() {
        DocumentoFirma documento = documentoConId(UUID.randomUUID());
        DocumentoEvento e1 = eventoEncadenado(documento, 1, HashChainCalculator.GENESIS, DocumentoEvento.TIPO_DILIGENCIADO);
        // Se reconstruye e1 con una descripción distinta pero CONSERVANDO el hashEvento
        // original -- simula una fila editada directamente en la base de datos sin pasar
        // por DocumentoTimelineService: el hash recalculado ya no coincide con el guardado.
        DocumentoEvento e1Alterado = new DocumentoEvento(documento, e1.getTipoEvento(), e1.getActorTipo(), null, null,
                "descripción manipulada", e1.getIp(), e1.getUserAgent(), e1.getHashDocumentoEnEvento(), e1.getSecuencia(),
                e1.getHashEventoAnterior(), e1.getHashEvento(), e1.getOcurridoEn());
        when(documentoEventoRepository.findByDocumentoIdOrderBySecuenciaAsc(documento.getId())).thenReturn(List.of(e1Alterado));

        ResultadoVerificacionCadena resultado = verificador().verificar(documento);

        assertFalse(resultado.integra());
        assertEquals(1L, resultado.secuenciaRota());
        verify(documentIntegrityGuard).marcarAlteradoEnNuevaTransaccion(eq(documento), any(), eq(DocumentoEvento.ACTOR_SISTEMA), eq(null));
    }

    // Ataque de truncamiento (ver V54__documento_checkpoints_anclas_diarias.sql): un
    // checkpoint de ayer registró más eventos de los que existen hoy -- la cadena que queda
    // (e1, e2) está perfectamente bien encadenada por sí sola, así que sin esta comprobación
    // extra el verificador la daría por íntegra sin detectar que falta e3.
    @Test
    void unCheckpointConMasEventosQueLosActualesSeDetectaComoTruncamiento() {
        DocumentoFirma documento = documentoConId(UUID.randomUUID());
        DocumentoEvento e1 = eventoEncadenado(documento, 1, HashChainCalculator.GENESIS, DocumentoEvento.TIPO_DILIGENCIADO);
        DocumentoEvento e2 = eventoEncadenado(documento, 2, e1.getHashEvento(), DocumentoEvento.TIPO_EMITIDO);
        when(documentoEventoRepository.findByDocumentoIdOrderBySecuenciaAsc(documento.getId())).thenReturn(List.of(e1, e2));
        when(checkpointRepository.maximoCantidadEventosRegistrado(documento.getId())).thenReturn(java.util.Optional.of(3L));

        ResultadoVerificacionCadena resultado = verificador().verificar(documento);

        assertFalse(resultado.integra());
        assertEquals(3L, resultado.secuenciaRota());
        verify(documentIntegrityGuard).marcarAlteradoEnNuevaTransaccion(eq(documento), any(), eq(DocumentoEvento.ACTOR_SISTEMA), eq(null));
    }

    @Test
    void unCheckpointConMenosOIgualesEventosNoDisparaTruncamiento() {
        DocumentoFirma documento = documentoConId(UUID.randomUUID());
        DocumentoEvento e1 = eventoEncadenado(documento, 1, HashChainCalculator.GENESIS, DocumentoEvento.TIPO_DILIGENCIADO);
        DocumentoEvento e2 = eventoEncadenado(documento, 2, e1.getHashEvento(), DocumentoEvento.TIPO_EMITIDO);
        when(documentoEventoRepository.findByDocumentoIdOrderBySecuenciaAsc(documento.getId())).thenReturn(List.of(e1, e2));
        when(checkpointRepository.maximoCantidadEventosRegistrado(documento.getId())).thenReturn(java.util.Optional.of(2L));

        ResultadoVerificacionCadena resultado = verificador().verificar(documento);

        assertTrue(resultado.integra());
        verify(documentIntegrityGuard, never()).marcarAlteradoEnNuevaTransaccion(any(), any(), any(), any());
    }
}
