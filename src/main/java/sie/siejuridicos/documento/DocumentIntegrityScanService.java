package sie.siejuridicos.documento;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// Escaneo individual, cada uno en su propia transacción (un fallo en un documento no debe
// tumbar el resto del lote, ver DocumentIntegrityScanner), de las 4 capas de integridad
// que de otro modo solo correrían cuando alguien pide una verificación puntual: cadena de
// hashes del timeline, firma criptográfica de aplicación, hash SHA-256 del contenido real
// descifrado, y código de verificación HMAC. Se detiene en la primera capa que marque el
// documento ALTERADO (ver DocumentIntegrityGuard, que ya dispara la alerta a gerencia por
// su cuenta vía DocumentoAlteradoEvent).
@Service
public class DocumentIntegrityScanService {

    private final DocumentoFirmaRepository documentoFirmaRepository;
    private final DocumentoEventoChainVerifier documentoEventoChainVerifier;
    private final ApplicationSignatureVerifier applicationSignatureVerifier;
    private final DocumentIntegrityGuard documentIntegrityGuard;
    private final EnvelopeEncryptionService envelopeEncryptionService;
    private final CodigoVerificacionCalculator codigoVerificacionCalculator;

    public DocumentIntegrityScanService(DocumentoFirmaRepository documentoFirmaRepository,
                                         DocumentoEventoChainVerifier documentoEventoChainVerifier,
                                         ApplicationSignatureVerifier applicationSignatureVerifier,
                                         DocumentIntegrityGuard documentIntegrityGuard,
                                         EnvelopeEncryptionService envelopeEncryptionService,
                                         CodigoVerificacionCalculator codigoVerificacionCalculator) {
        this.documentoFirmaRepository = documentoFirmaRepository;
        this.documentoEventoChainVerifier = documentoEventoChainVerifier;
        this.applicationSignatureVerifier = applicationSignatureVerifier;
        this.documentIntegrityGuard = documentIntegrityGuard;
        this.envelopeEncryptionService = envelopeEncryptionService;
        this.codigoVerificacionCalculator = codigoVerificacionCalculator;
    }

    // true si este escaneo (no uno anterior) dejó el documento marcado ALTERADO.
    @Transactional
    public boolean escanearDocumento(UUID documentoId) {
        DocumentoFirma documento = documentoFirmaRepository.findById(documentoId).orElse(null);
        if (documento == null || DocumentoFirma.INTEGRIDAD_ALTERADO.equals(documento.getEstadoIntegridad())) {
            return false;
        }

        ResultadoVerificacionCadena cadena = documentoEventoChainVerifier.verificar(documento);
        if (!cadena.integra()) {
            return true; // el propio verificador ya marcó ALTERADO
        }

        if (!applicationSignatureVerifier.verificar(documento)) {
            documentIntegrityGuard.marcarAlteradoEnNuevaTransaccion(documento,
                    "La firma criptográfica de aplicación ya no coincide con los datos actuales del documento "
                            + "(detección proactiva del barrido periódico de integridad, sin que nadie lo verificara "
                            + "manualmente).",
                    DocumentoEvento.ACTOR_SISTEMA, null);
            return true;
        }

        if (documento.getHashContenido() != null) {
            byte[] contenidoReal = envelopeEncryptionService.leerContenido(documento);
            String hashRecalculado = HashUtil.sha256Hex(contenidoReal);
            if (!hashRecalculado.equalsIgnoreCase(documento.getHashContenido())) {
                documentIntegrityGuard.marcarAlteradoEnNuevaTransaccion(documento,
                        "El contenido real del documento (descifrado) ya no coincide con su hash SHA-256 original "
                                + "(detección proactiva del barrido periódico de integridad).",
                        DocumentoEvento.ACTOR_SISTEMA, null);
                return true;
            }
        }

        if (documento.getCodigoVerificacion() != null && documento.getNumeroSecuencia() != null) {
            String recalculado = codigoVerificacionCalculator.calcular(documento.getId(), documento.getNumeroSecuencia());
            if (!recalculado.equalsIgnoreCase(documento.getCodigoVerificacion())) {
                documentIntegrityGuard.marcarAlteradoEnNuevaTransaccion(documento,
                        "El código de verificación recalculado ya no coincide con el guardado "
                                + "(detección proactiva del barrido periódico de integridad).",
                        DocumentoEvento.ACTOR_SISTEMA, null);
                return true;
            }
        }

        return false;
    }
}
