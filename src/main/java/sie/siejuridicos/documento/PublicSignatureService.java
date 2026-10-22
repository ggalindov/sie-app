package sie.siejuridicos.documento;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sie.siejuridicos.common.exception.ConflictoNegocioException;
import sie.siejuridicos.common.exception.EntidadInvalidaException;
import sie.siejuridicos.documento.dto.ConsentimientoRequest;
import sie.siejuridicos.documento.dto.DocumentDownload;
import sie.siejuridicos.documento.dto.FirmaResumenDto;
import sie.siejuridicos.documento.dto.GeolocalizacionRequest;
import sie.siejuridicos.documento.events.OtpGeneradoEvent;

import java.util.Locale;

// Fachada del flujo PÚBLICO de firma (sin JWT, protegido solo por el token opaco del link
// único): un firmante nunca interactúa directamente con DocumentoFirmante/OtpService/etc,
// solo a través de aquí. Ver PublicSignatureController para el mapeo HTTP.
//
// Ajuste de seguridad (ver IdentidadInvalidaException): antes de poder pedir el código OTP,
// quien abrió el link debe demostrar que conoce el número de documento (cédula/NIT)
// registrado para esa parte -- segundo factor independiente del canal por el que llegó el
// link. A diferencia del motor de referencia (que exige completar todo el flujo desde un
// teléfono celular), SIE no restringe el dispositivo -- un representante de empresa puede
// razonablemente firmar desde su computador de oficina (ver decisión del plan de
// adaptación).
@Service
public class PublicSignatureService {

    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final long TAMANO_MAXIMO_IMAGEN_BYTES = 8L * 1024 * 1024;
    // ~10.000 puntos {x,y,t,p,ordenTrazo} de sobra para cualquier firma real; límite
    // defensivo contra un cliente que mande un JSON artificialmente enorme.
    private static final int TAMANO_MAXIMO_TRAZO_VECTORIAL_CARACTERES = 1024 * 1024;
    // documento_firmantes.huella_dispositivo es VARCHAR(64) (ver V48) -- bug real encontrado
    // en producción: el frontend mandaba el navigator.userAgent crudo ahí, que por sí solo ya
    // supera 64 caracteres en cualquier celular real, y Postgres rechazaba el UPDATE entero
    // con un genérico "value too long", que el manejador global traducía a un 503 "servicio
    // no disponible" sin ninguna pista real del problema. El frontend ya quedó arreglado
    // (ahora manda un hash SHA-256 de 64 caracteres, ver sha256Hex en el wizard), pero esta
    // validación evita que el mismo error silencioso vuelva a pasar si algo cambia del lado
    // del cliente o alguien llama este endpoint directo.
    private static final int TAMANO_MAXIMO_HUELLA_DISPOSITIVO_CARACTERES = 64;

    private final SignerTokenService signerTokenService;
    private final OtpService otpService;
    private final EvidenceCaptureService evidenceCaptureService;
    private final DocumentoFirmaService documentoFirmaService;
    private final EnvelopeEncryptionService envelopeEncryptionService;
    private final EvidenceCertificateService evidenceCertificateService;
    private final ApplicationEventPublisher eventPublisher;
    private final int identidadIntentosMaximos;

    public PublicSignatureService(SignerTokenService signerTokenService,
                                   OtpService otpService,
                                   EvidenceCaptureService evidenceCaptureService,
                                   DocumentoFirmaService documentoFirmaService,
                                   EnvelopeEncryptionService envelopeEncryptionService,
                                   EvidenceCertificateService evidenceCertificateService,
                                   ApplicationEventPublisher eventPublisher,
                                   @Value("${app.documento.otp.intentos-identidad-maximos:5}") int identidadIntentosMaximos) {
        this.signerTokenService = signerTokenService;
        this.otpService = otpService;
        this.evidenceCaptureService = evidenceCaptureService;
        this.documentoFirmaService = documentoFirmaService;
        this.envelopeEncryptionService = envelopeEncryptionService;
        this.evidenceCertificateService = evidenceCertificateService;
        this.eventPublisher = eventPublisher;
        this.identidadIntentosMaximos = identidadIntentosMaximos;
    }

    @Transactional(readOnly = true)
    public FirmaResumenDto resumen(String token) {
        return FirmaResumenDto.from(signerTokenService.resolverParaConsulta(token));
    }

    // Declinar firmar: a propósito solo exige un enlace válido y que sea su turno (igual
    // que resolver()), NO exige haber verificado identidad ni el OTP -- pedirle a alguien
    // que pruebe quién es solo para poder decir "no quiero firmar" es una fricción
    // injustificada. Anula el documento completo de inmediato (ver
    // DocumentoFirmaService#registrarRechazoDeParte).
    @Transactional
    public void rechazar(String token, String motivo, String ip, String userAgent) {
        DocumentoFirmante firmante = signerTokenService.resolver(token);
        documentoFirmaService.registrarRechazoDeParte(firmante, motivo, ip, userAgent);
    }

    // Primer factor propio del firmante (además de poseer el link): su número de
    // documento tal como quedó registrado al asignarlo. Al verificarse, dispara
    // automáticamente el envío del código OTP por el mismo canal del enlace -- el
    // firmante nunca "solicita" el OTP por su cuenta, solo llega después de este paso.
    @Transactional
    public void verificarIdentidad(String token, String numeroDocumento, String ip, String userAgent) {
        DocumentoFirmante firmante = signerTokenService.resolver(token);
        if (firmante.agotoIntentosIdentidad(identidadIntentosMaximos)) {
            throw new IdentidadInvalidaException("Se agotaron los intentos para verificar tu identidad. "
                    + "Pide a SIE Jurídicos que te reenvíe el enlace.");
        }
        if (numeroDocumento == null || !normalizar(numeroDocumento).equals(normalizar(firmante.getDocumentoIdentidadCapturado()))) {
            firmante.registrarIntentoIdentidadFallido();
            throw new IdentidadInvalidaException("El número de documento ingresado no coincide con el registrado para esta firma.");
        }
        firmante.registrarIdentidadVerificada();
        firmante.registrarCapturaTecnica(ip, userAgent);
        enviarNuevoOtp(firmante, ip);
    }

    // Reenvío manual del código (ej. no llegó, o venció): solo disponible una vez la
    // identidad ya quedó verificada, para no reabrir esta puerta a quien no superó ese paso.
    @Transactional
    public void reenviarOtp(String token, String ip) {
        DocumentoFirmante firmante = signerTokenService.resolver(token);
        if (firmante.getIdentidadVerificadaEn() == null) {
            throw new IdentidadInvalidaException("Primero debes verificar tu número de documento.");
        }
        enviarNuevoOtp(firmante, ip);
    }

    private void enviarNuevoOtp(DocumentoFirmante firmante, String ip) {
        String canal = firmante.getCanalNotificacion() != null ? firmante.getCanalNotificacion() : DocumentoFirmante.CANAL_EMAIL;
        String codigoPlano = otpService.solicitar(firmante, canal, ip);
        firmante.setEstado(DocumentoFirmante.ESTADO_NOTIFICADO);
        eventPublisher.publishEvent(new OtpGeneradoEvent(firmante, codigoPlano));
    }

    @Transactional
    public void verificarOtp(String token, String codigo) {
        DocumentoFirmante firmante = signerTokenService.resolver(token);
        if (firmante.getIdentidadVerificadaEn() == null) {
            throw new IdentidadInvalidaException("Primero debes verificar tu número de documento.");
        }
        otpService.verificar(firmante, codigo);
        firmante.setEstado(DocumentoFirmante.ESTADO_OTP_VERIFICADO);
    }

    @Transactional
    public void registrarConsentimiento(String token, ConsentimientoRequest request) {
        DocumentoFirmante firmante = verificarPrerrequisitoOtp(token);
        if (!request.aceptar()) {
            throw new EntidadInvalidaException("Debes aceptar expresamente el consentimiento para continuar.");
        }
        evidenceCaptureService.registrarConsentimiento(firmante, request.textoAceptado());
    }

    @Transactional
    public void registrarGeolocalizacion(String token, GeolocalizacionRequest request) {
        DocumentoFirmante firmante = verificarPrerrequisitoOtp(token);
        if (request.rechazada() || request.lat() == null || request.lon() == null) {
            evidenceCaptureService.registrarGeolocalizacionRechazada(firmante);
        } else {
            evidenceCaptureService.registrarGeolocalizacion(firmante, request.lat(), request.lon(), request.precisionM());
        }
    }

    @Transactional
    public void registrarIdentidad(String token, byte[] imagen, String contentType) {
        DocumentoFirmante firmante = verificarPrerrequisitoOtp(token);
        validarImagen(imagen, "El documento de identidad debe ser una foto JPEG o PNG.");
        evidenceCaptureService.registrarIdentidad(firmante, imagen, contentType);
    }

    // Foto tomada con la cámara del dispositivo justo antes de firmar: NO es
    // reconocimiento facial ni biometría de matching, solo se conserva como evidencia de
    // seguridad adicional.
    @Transactional
    public void registrarVerificacionHumana(String token, byte[] imagen, String contentType) {
        DocumentoFirmante firmante = verificarPrerrequisitoOtp(token);
        validarImagen(imagen, "La foto de verificación debe ser una imagen JPEG o PNG.");
        evidenceCaptureService.registrarVerificacionHumana(firmante, imagen, contentType);
    }

    @Transactional
    public void firmar(String token, byte[] firmaPng, String ip, String userAgent, String huellaDispositivo,
                        String contextoDispositivoJson, String senalesProxyDetectadas, String trazoVectorialJson) {
        DocumentoFirmante firmante = verificarPrerrequisitoOtp(token);
        validarImagen(firmaPng, "La firma capturada no es una imagen válida.");
        if (trazoVectorialJson != null && trazoVectorialJson.length() > TAMANO_MAXIMO_TRAZO_VECTORIAL_CARACTERES) {
            throw new EntidadInvalidaException("El trazo de la firma es demasiado grande.");
        }
        if (huellaDispositivo != null && huellaDispositivo.length() > TAMANO_MAXIMO_HUELLA_DISPOSITIVO_CARACTERES) {
            throw new EntidadInvalidaException("La huella de dispositivo enviada no es válida.");
        }
        if (firmante.getConsentimientoAceptadoEn() == null) {
            throw new EntidadInvalidaException("Debes aceptar el consentimiento antes de firmar.");
        }
        if (firmante.getDocumentoIdentidadImagen() == null) {
            throw new EntidadInvalidaException("Debes adjuntar la foto de tu documento de identidad antes de firmar.");
        }
        if (firmante.getFotoVerificacionHumana() == null) {
            throw new EntidadInvalidaException("Debes tomarte una foto de verificación antes de firmar.");
        }
        // Pedido explícito del usuario: la ubicación pasó de ser evidencia "mejor esfuerzo"
        // a un requisito obligatorio del proceso -- antes solo quedaba registrado el rechazo
        // y se dejaba firmar igual. El frontend ya no deja avanzar sin ella (ver
        // onPedirGeolocalizacion en el wizard), pero esta validación es la que de verdad lo
        // hace obligatorio: sin ella, cualquiera podría llamar /firmar directo saltándose el
        // paso del navegador.
        if (firmante.isGeolocalizacionRechazada() || firmante.getGeolocalizacionCapturadaEn() == null) {
            throw new EntidadInvalidaException("Debes compartir tu ubicación antes de firmar. Vuelve al paso de ubicación e inténtalo de nuevo.");
        }
        firmante.registrarCapturaTecnica(ip, userAgent);
        evidenceCaptureService.registrarContextoDispositivo(firmante, huellaDispositivo, contextoDispositivoJson, senalesProxyDetectadas);
        documentoFirmaService.registrarFirmaDeParte(firmante, firmaPng, trazoVectorialJson);
    }

    @Transactional(readOnly = true)
    public DocumentDownload documentoFinal(String token) {
        DocumentoFirmante firmante = signerTokenService.resolverParaConsulta(token);
        DocumentoFirma documento = firmante.getDocumento();
        boolean disponible = DocumentoFirma.ESTADO_DOC_REMITIDO.equals(documento.getEstadoDocumento())
                || DocumentoFirma.ESTADO_DOC_ENCRIPTADO.equals(documento.getEstadoDocumento());
        if (!disponible) {
            throw new ConflictoNegocioException("El documento aún no está disponible: faltan firmas de otras partes.");
        }
        return new DocumentDownload(envelopeEncryptionService.leerContenido(documento), documento.getNombre(), "application/pdf");
    }

    // El documento tal como va a quedar firmado, para que la parte lo LEA antes de
    // firmarlo: el consentimiento que acepta dice "declaro que firmo este documento".
    @Transactional(readOnly = true)
    public DocumentDownload documentoParaRevisar(String token) {
        DocumentoFirmante firmante = signerTokenService.resolverParaConsulta(token);
        boolean verificado = DocumentoFirmante.ESTADO_OTP_VERIFICADO.equals(firmante.getEstado())
                || DocumentoFirmante.ESTADO_FIRMADO.equals(firmante.getEstado());
        if (!verificado) {
            throw new TokenFirmaInvalidoException("Primero debes verificar tu identidad y el código enviado.");
        }
        DocumentoFirma documento = firmante.getDocumento();
        return new DocumentDownload(envelopeEncryptionService.leerContenido(documento), documento.getNombre(), "application/pdf");
    }

    // Certificado de evidencia del PROPIO firmante -- nunca expone datos de otros firmantes del mismo documento.
    @Transactional(readOnly = true)
    public byte[] certificadoEvidencia(String token) {
        DocumentoFirmante firmante = signerTokenService.resolverParaConsulta(token);
        if (!DocumentoFirmante.ESTADO_FIRMADO.equals(firmante.getEstado())) {
            throw new ConflictoNegocioException("Tu certificado de evidencia estará disponible una vez completes tu firma.");
        }
        return evidenceCertificateService.generarPorFirmante(firmante.getId());
    }

    private DocumentoFirmante verificarPrerrequisitoOtp(String token) {
        DocumentoFirmante firmante = signerTokenService.resolver(token);
        if (!DocumentoFirmante.ESTADO_OTP_VERIFICADO.equals(firmante.getEstado())) {
            throw new TokenFirmaInvalidoException("Primero debes verificar tu identidad y el código enviado.");
        }
        return firmante;
    }

    private String normalizar(String numeroDocumento) {
        if (numeroDocumento == null) {
            return "";
        }
        return numeroDocumento.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private void validarImagen(byte[] contenido, String mensajeError) {
        if (contenido == null || contenido.length == 0) {
            throw new EntidadInvalidaException("El archivo está vacío.");
        }
        if (contenido.length > TAMANO_MAXIMO_IMAGEN_BYTES) {
            throw new EntidadInvalidaException("El archivo supera el tamaño máximo permitido (8MB).");
        }
        if (!coincidePrefijo(contenido, PNG_MAGIC) && !coincidePrefijo(contenido, JPEG_MAGIC)) {
            throw new EntidadInvalidaException(mensajeError);
        }
    }

    private boolean coincidePrefijo(byte[] contenido, byte[] prefijo) {
        if (contenido.length < prefijo.length) {
            return false;
        }
        for (int i = 0; i < prefijo.length; i++) {
            if (contenido[i] != prefijo[i]) {
                return false;
            }
        }
        return true;
    }
}
