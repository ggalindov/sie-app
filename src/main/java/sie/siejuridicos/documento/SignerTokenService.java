package sie.siejuridicos.documento;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

// Genera y resuelve el token opaco del link único de firma (/firmar/{token} en el
// frontend). Solo se guarda el HASH del token en base de datos
// (documento_firmantes.token_hash) para que una fuga de la base de datos no permita
// reconstruir los links activos.
@Service
public class SignerTokenService {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();
    private final DocumentoFirmanteRepository documentoFirmanteRepository;
    private final long tokenFirmaDias;

    public SignerTokenService(DocumentoFirmanteRepository documentoFirmanteRepository,
                               @Value("${app.documento.token-firma-dias:7}") long tokenFirmaDias) {
        this.documentoFirmanteRepository = documentoFirmanteRepository;
        this.tokenFirmaDias = tokenFirmaDias;
    }

    public String hash(String tokenPlano) {
        return HashUtil.sha256Hex(tokenPlano.getBytes(StandardCharsets.UTF_8));
    }

    // Genera un token nuevo para el firmante y lo deja listo para notificar; devuelve el
    // valor EN CLARO (única vez que existe fuera de la memoria del cliente que lo recibe)
    // para que el llamador arme el enlace a enviar por WhatsApp/correo.
    public String emitirToken(DocumentoFirmante firmante) {
        String tokenPlano = generarToken(firmante);
        firmante.reiniciarRecordatorios(LocalDateTime.now());
        return tokenPlano;
    }

    // Enlace nuevo para un recordatorio: invalida el anterior (solo se guarda el hash, no
    // se puede reenviar el mismo) pero conserva y avanza el contador de recordatorios.
    public String renovarPorRecordatorio(DocumentoFirmante firmante) {
        String tokenPlano = generarToken(firmante);
        firmante.registrarRecordatorio(LocalDateTime.now());
        return tokenPlano;
    }

    // Invalida el enlace vigente sin emitir otro: el token deja de resolver de inmediato.
    public void revocar(DocumentoFirmante firmante) {
        firmante.setTokenHash(null);
        firmante.setTokenExpiraEn(null);
    }

    // Firma secuencial: es el turno del firmante cuando todos los de orden anterior ya
    // firmaron o rechazaron (un rechazo cierra su turno).
    public boolean esSuTurno(DocumentoFirmante firmante) {
        return !documentoFirmanteRepository.existsByDocumentoIdAndOrdenLessThanAndEstadoNotIn(
                firmante.getDocumento().getId(), firmante.getOrden(),
                List.of(DocumentoFirmante.ESTADO_FIRMADO, DocumentoFirmante.ESTADO_RECHAZADO));
    }

    private String generarToken(DocumentoFirmante firmante) {
        byte[] aleatorio = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(aleatorio);
        String tokenPlano = Base64.getUrlEncoder().withoutPadding().encodeToString(aleatorio);
        firmante.setTokenHash(hash(tokenPlano));
        firmante.setTokenExpiraEn(LocalDateTime.now().plusDays(tokenFirmaDias));
        return tokenPlano;
    }

    // Variante de SOLO LECTURA de resolver(): además de los firmantes con turno activo,
    // acepta al que ya FIRMÓ o RECHAZÓ con este enlace, para que pueda ver su estado final
    // ("completado" / "rechazado"). Nunca debe usarse para una acción que modifique la firma.
    public DocumentoFirmante resolverParaConsulta(String tokenPlano) {
        DocumentoFirmante firmante = documentoFirmanteRepository.findByTokenHash(hash(tokenPlano))
                .orElseThrow(() -> new TokenFirmaInvalidoException("El enlace de firma no es válido."));
        if (DocumentoFirmante.ESTADO_FIRMADO.equals(firmante.getEstado())
                || DocumentoFirmante.ESTADO_RECHAZADO.equals(firmante.getEstado())) {
            return firmante;
        }
        return resolver(tokenPlano);
    }

    public DocumentoFirmante resolver(String tokenPlano) {
        DocumentoFirmante firmante = documentoFirmanteRepository.findByTokenHash(hash(tokenPlano))
                .orElseThrow(() -> new TokenFirmaInvalidoException("El enlace de firma no es válido."));
        if (DocumentoFirmante.ESTADO_REVOCADO.equals(firmante.getEstado())
                || DocumentoFirmante.ESTADO_EXPIRADO.equals(firmante.getEstado())) {
            throw new TokenFirmaInvalidoException("El enlace de firma ya no está vigente. Solicita que te reenvíen uno nuevo.");
        }
        if (firmante.getTokenExpiraEn() == null || LocalDateTime.now().isAfter(firmante.getTokenExpiraEn())) {
            throw new TokenFirmaInvalidoException("El enlace de firma venció. Solicita que te reenvíen uno nuevo.");
        }
        if (DocumentoFirmante.ESTADO_FIRMADO.equals(firmante.getEstado())
                || DocumentoFirmante.ESTADO_RECHAZADO.equals(firmante.getEstado())) {
            throw new TokenFirmaInvalidoException("Este documento ya fue procesado con este enlace.");
        }
        if (!esSuTurno(firmante)) {
            throw new TokenFirmaInvalidoException("Aún no es tu turno para firmar: los firmantes anteriores deben firmar primero.");
        }
        return firmante;
    }
}
