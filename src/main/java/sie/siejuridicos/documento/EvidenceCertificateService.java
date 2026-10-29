package sie.siejuridicos.documento;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sie.siejuridicos.common.exception.RecursoNoEncontradoException;
import sie.siejuridicos.documento.pdf.EvidenceCertificateData;
import sie.siejuridicos.documento.pdf.EvidenceCertificatePdfGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// Orquesta la generación del certificado de evidencia de firma: junta los datos ya
// calculados (documento, firmantes, verificación de la cadena de eventos, firma de
// aplicación, sellos de tiempo) en un EvidenceCertificateData y delega el diagramado en
// EvidenceCertificatePdfGenerator (que no conoce JPA ni criptografía -- separación
// deliberada entre "verificar" y "dibujar").
@Service
public class EvidenceCertificateService {

    private final DocumentoFirmaRepository documentoFirmaRepository;
    private final DocumentoFirmanteRepository documentoFirmanteRepository;
    private final DocumentoEventoChainVerifier documentoEventoChainVerifier;
    private final ApplicationSignatureVerifier applicationSignatureVerifier;
    private final Rfc3161TimestampClient rfc3161TimestampClient;
    private final EvidenceCertificatePdfGenerator pdfGenerator;
    private final String sitioPublicoUrl;

    public EvidenceCertificateService(DocumentoFirmaRepository documentoFirmaRepository,
                                       DocumentoFirmanteRepository documentoFirmanteRepository,
                                       DocumentoEventoChainVerifier documentoEventoChainVerifier,
                                       ApplicationSignatureVerifier applicationSignatureVerifier,
                                       Rfc3161TimestampClient rfc3161TimestampClient,
                                       EvidenceCertificatePdfGenerator pdfGenerator,
                                       @Value("${app.documento.sitio-web}") String sitioPublicoUrl) {
        this.documentoFirmaRepository = documentoFirmaRepository;
        this.documentoFirmanteRepository = documentoFirmanteRepository;
        this.documentoEventoChainVerifier = documentoEventoChainVerifier;
        this.applicationSignatureVerifier = applicationSignatureVerifier;
        this.rfc3161TimestampClient = rfc3161TimestampClient;
        this.pdfGenerator = pdfGenerator;
        this.sitioPublicoUrl = sitioPublicoUrl;
    }

    // Para staff autenticado: todos los firmantes del documento.
    @Transactional(readOnly = true)
    public byte[] generarConsolidadoPorDocumento(UUID documentoId) {
        DocumentoFirma documento = documento(documentoId);
        List<DocumentoFirmante> firmantes = documentoFirmanteRepository.findByDocumentoIdOrderByOrdenAsc(documentoId);
        return pdfGenerator.generar(construirDatos(documento, firmantes, true));
    }

    // Para el flujo público por token: SOLO su propio firmante, nunca los demás -- evita
    // que la descarga de una parte exponga los datos forenses de las otras. Tampoco se le
    // expone a ESE firmante su propio detalle forense (IP, user-agent, huella de
    // dispositivo, etc.) -- ver el comentario de EvidenceCertificateData.detalleTecnicoCompleto.
    @Transactional(readOnly = true)
    public byte[] generarPorFirmante(UUID firmanteId) {
        DocumentoFirmante firmante = documentoFirmanteRepository.findById(firmanteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró el firmante solicitado."));
        return pdfGenerator.generar(construirDatos(firmante.getDocumento(), List.of(firmante), false));
    }

    private DocumentoFirma documento(UUID documentoId) {
        return documentoFirmaRepository.findById(documentoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró el documento solicitado."));
    }

    private EvidenceCertificateData construirDatos(DocumentoFirma documento, List<DocumentoFirmante> firmantes,
                                                     boolean detalleTecnicoCompleto) {
        ResultadoVerificacionCadena cadena = documentoEventoChainVerifier.verificar(documento);
        boolean firmaAppValida = applicationSignatureVerifier.verificar(documento);
        boolean selloDocumentoAplicado = documento.getRfc3161Token() != null;
        boolean selloDocumentoValido = selloDocumentoAplicado && documento.getHashContenido() != null
                && rfc3161TimestampClient.verificarToken(documento.getRfc3161Token(), HashUtil.hexToBytes(documento.getHashContenido()));

        List<EvidenceCertificateData.FirmanteData> firmantesData = firmantes.stream()
                .map(firmante -> construirFirmanteData(firmante, detalleTecnicoCompleto))
                .toList();

        return new EvidenceCertificateData(
                documento.getId().toString(),
                documento.getNombre(),
                documento.getNumeroSecuencia(),
                documento.getFechaCreacion(),
                documento.getHashContenido(),
                documento.getEstadoDocumento(),
                firmaAppValida,
                documento.getFirmaAppKid(),
                selloDocumentoAplicado,
                documento.getRfc3161UrlTsa(),
                documento.getRfc3161SelladoEn(),
                cadena.integra(),
                cadena.detalle(),
                documento.getCodigoVerificacion(),
                documento.getCodigoVerificacion() != null
                        ? sitioPublicoUrl + "/verificar-documento?codigo=" + documento.getCodigoVerificacion()
                        : null,
                LocalDateTime.now(),
                firmantesData,
                detalleTecnicoCompleto
        );
    }

    private EvidenceCertificateData.FirmanteData construirFirmanteData(DocumentoFirmante firmante, boolean detalleTecnicoCompleto) {
        boolean selloAplicado = firmante.getRfc3161Token() != null;
        boolean selloValido = selloAplicado && firmante.getHashEvidencia() != null
                && rfc3161TimestampClient.verificarToken(firmante.getRfc3161Token(), HashUtil.hexToBytes(firmante.getHashEvidencia()));

        boolean verificacionHumanaCapturada = firmante.getFotoVerificacionHumana() != null;
        String verificacionHumanaHash = verificacionHumanaCapturada ? HashUtil.sha256Hex(firmante.getFotoVerificacionHumana()) : null;

        return new EvidenceCertificateData.FirmanteData(
                firmante.getTipoFirmante(),
                firmante.getNombreCapturado(),
                firmante.getDocumentoIdentidadCapturado(),
                firmante.getEstado(),
                firmante.getCreadoEn(),
                firmante.getTokenExpiraEn(),
                firmante.getIpCaptura(),
                firmante.getUserAgentCaptura(),
                firmante.getHuellaDispositivo(),
                firmante.getSenalesProxyDetectadas(),
                firmante.isGeolocalizacionRechazada(),
                geolocalizacionTexto(firmante, detalleTecnicoCompleto),
                firmante.getGeolocalizacionCapturadaEn(),
                firmante.getConsentimientoTexto(),
                firmante.getConsentimientoAceptadoEn(),
                firmante.getHashEvidencia(),
                firmante.getFirmadoEn(),
                selloAplicado,
                firmante.getRfc3161UrlTsa(),
                selloValido,
                firmante.getFirmaImagenPng(),
                firmante.getDocumentoIdentidadImagen(),
                verificacionHumanaCapturada,
                verificacionHumanaHash,
                firmante.getFotoVerificacionHumana()
        );
    }

    // Punto 14 de las mejoras pedidas sobre el motor "hermano" (UCN): al firmante se le
    // enmascara la geolocalización (redondeada a ~2 decimales, ~1 km de precisión, sin el
    // radio de precisión exacto del GPS) en vez de las coordenadas exactas que SÍ recibe el
    // staff autenticado -- es evidencia forense completa solo para una disputa real, no un
    // dato que el propio firmante necesite ver con precisión de metros.
    private static String geolocalizacionTexto(DocumentoFirmante firmante, boolean detalleTecnicoCompleto) {
        BigDecimal lat = firmante.getGeolocalizacionLat();
        BigDecimal lon = firmante.getGeolocalizacionLon();
        if (lat == null || lon == null) {
            return null;
        }
        if (detalleTecnicoCompleto) {
            BigDecimal precision = firmante.getGeolocalizacionPrecisionM();
            return lat + ", " + lon + (precision != null ? " (precisión +/-" + precision + " m)" : "");
        }
        BigDecimal latAprox = lat.setScale(2, java.math.RoundingMode.HALF_UP);
        BigDecimal lonAprox = lon.setScale(2, java.math.RoundingMode.HALF_UP);
        return latAprox + ", " + lonAprox + " (ubicación aproximada)";
    }
}
