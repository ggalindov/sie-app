package sie.siejuridicos.documento;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

// Firma secuencial (ver SignerTokenService#esSuTurno/resolver): nadie puede firmar fuera de
// su turno aunque tenga un enlace válido y sin vencer -- pedido explícito del usuario
// (partes asignadas por el gerente, firmando en el orden en que se asignaron). Estas
// pruebas cubren exactamente ese "bypass del orden secuencial" que pide la auditoría de
// seguridad: un enlace técnicamente válido (hash correcto, sin vencer, sin usar) debe
// seguir siendo rechazado si los firmantes de orden anterior no han terminado.
@ExtendWith(MockitoExtension.class)
class SignerTokenServiceTest {

    @Mock
    private DocumentoFirmanteRepository documentoFirmanteRepository;

    private SignerTokenService servicio() {
        return new SignerTokenService(documentoFirmanteRepository, 7);
    }

    private DocumentoFirma documentoConId(UUID id) {
        DocumentoFirma documento = new DocumentoFirma(null, null, "Documento de prueba", "application/pdf", new byte[0]);
        // El id real lo asigna Hibernate al persistir (estrategia UUID); en la prueba se
        // fija por reflexión para poder construir un firmante con un documento "ya guardado".
        try {
            var campoId = DocumentoFirma.class.getDeclaredField("id");
            campoId.setAccessible(true);
            campoId.set(documento, id);
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
        return documento;
    }

    private DocumentoFirmante firmante(DocumentoFirma documento, int orden, String estado, String tokenHash, LocalDateTime expira) {
        DocumentoFirmante f = new DocumentoFirmante(documento, DocumentoFirmante.TIPO_PARTE, null, null, "PERSONA_NATURAL",
                "Firmante de prueba", "123", orden);
        f.setEstado(estado);
        f.setTokenHash(tokenHash);
        f.setTokenExpiraEn(expira);
        return f;
    }

    @Test
    void resolverRechazaUnTokenCuandoAunNoEsSuTurno() {
        UUID documentoId = UUID.randomUUID();
        DocumentoFirma documento = documentoConId(documentoId);
        DocumentoFirmante segundaParte = firmante(documento, 1, DocumentoFirmante.ESTADO_NOTIFICADO, "hash-valido",
                LocalDateTime.now().plusDays(1));

        when(documentoFirmanteRepository.findByTokenHash(any())).thenReturn(Optional.of(segundaParte));
        // Todavía existe un firmante de orden anterior (0) que no firmó ni rechazó: NO es su turno.
        when(documentoFirmanteRepository.existsByDocumentoIdAndOrdenLessThanAndEstadoNotIn(eq(documentoId), eq(1), any()))
                .thenReturn(true);

        SignerTokenService servicio = servicio();
        TokenFirmaInvalidoException ex = assertThrows(TokenFirmaInvalidoException.class,
                () -> servicio.resolver("token-plano-cualquiera"));
        assertTrue(ex.getMessage().contains("turno"));
    }

    @Test
    void resolverAceptaUnTokenCuandoYaEsSuTurno() {
        UUID documentoId = UUID.randomUUID();
        DocumentoFirma documento = documentoConId(documentoId);
        DocumentoFirmante primeraParte = firmante(documento, 0, DocumentoFirmante.ESTADO_NOTIFICADO, "hash-valido",
                LocalDateTime.now().plusDays(1));

        when(documentoFirmanteRepository.findByTokenHash(any())).thenReturn(Optional.of(primeraParte));
        // No hay firmantes de orden anterior a 0 por definición: siempre es su turno.
        when(documentoFirmanteRepository.existsByDocumentoIdAndOrdenLessThanAndEstadoNotIn(eq(documentoId), eq(0), any()))
                .thenReturn(false);

        DocumentoFirmante resuelto = servicio().resolver("token-plano-cualquiera");
        assertTrue(resuelto == primeraParte);
    }

    @Test
    void resolverRechazaUnTokenYaFirmado() {
        DocumentoFirma documento = documentoConId(UUID.randomUUID());
        DocumentoFirmante yaFirmado = firmante(documento, 0, DocumentoFirmante.ESTADO_FIRMADO, "hash-valido",
                LocalDateTime.now().plusDays(1));
        when(documentoFirmanteRepository.findByTokenHash(any())).thenReturn(Optional.of(yaFirmado));

        assertThrows(TokenFirmaInvalidoException.class, () -> servicio().resolver("token-plano-cualquiera"));
    }

    @Test
    void resolverRechazaUnTokenVencido() {
        DocumentoFirma documento = documentoConId(UUID.randomUUID());
        DocumentoFirmante vencido = firmante(documento, 0, DocumentoFirmante.ESTADO_NOTIFICADO, "hash-valido",
                LocalDateTime.now().minusMinutes(1));
        when(documentoFirmanteRepository.findByTokenHash(any())).thenReturn(Optional.of(vencido));

        assertThrows(TokenFirmaInvalidoException.class, () -> servicio().resolver("token-plano-cualquiera"));
    }

    @Test
    void resolverRechazaUnTokenRevocado() {
        DocumentoFirma documento = documentoConId(UUID.randomUUID());
        DocumentoFirmante revocado = firmante(documento, 0, DocumentoFirmante.ESTADO_REVOCADO, "hash-valido",
                LocalDateTime.now().plusDays(1));
        when(documentoFirmanteRepository.findByTokenHash(any())).thenReturn(Optional.of(revocado));

        assertThrows(TokenFirmaInvalidoException.class, () -> servicio().resolver("token-plano-cualquiera"));
    }

    @Test
    void resolverRechazaUnTokenQueNoExiste() {
        when(documentoFirmanteRepository.findByTokenHash(any())).thenReturn(Optional.empty());
        assertThrows(TokenFirmaInvalidoException.class, () -> servicio().resolver("token-inexistente"));
    }

    @Test
    void emitirTokenGuardaSoloElHashNuncaElValorEnClaro() {
        DocumentoFirma documento = documentoConId(UUID.randomUUID());
        DocumentoFirmante f = firmante(documento, 0, DocumentoFirmante.ESTADO_PENDIENTE, null, null);

        String tokenPlano = servicio().emitirToken(f);

        assertFalse(tokenPlano.equals(f.getTokenHash()), "el hash guardado nunca debe ser igual al token en claro");
        assertTrue(f.getTokenHash() != null && f.getTokenHash().length() == 64, "se espera un hash SHA-256 en hex (64 caracteres)");
    }
}
