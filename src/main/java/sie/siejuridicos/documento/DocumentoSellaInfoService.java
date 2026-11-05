package sie.siejuridicos.documento;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sie.siejuridicos.documento.events.DocumentoRemitidoEvent;
import sie.siejuridicos.documento.pdf.CierreCustodiaPdfAppender;
import sie.siejuridicos.documento.pdf.HojaCierreCustodiaData;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

// Cierra el ciclo de vida de un documento en cuanto todos los firmantes (staff y partes)
// completaron su firma: estampa las páginas de firma sobre el PDF original (ver
// DocumentoPdfStamper -- a diferencia del motor de referencia, aquí nunca se regenera el
// documento desde una plantilla, se usa el archivo que gerencia subió tal cual), aplica la
// firma digital PAdES embebida y el sello de tiempo RFC 3161, lo remite a todas las partes
// externas y lo cifra en reposo con envelope encryption, dejando cada paso asentado en la
// línea de tiempo del documento.
@Service
public class DocumentoSellaInfoService {

    private static final Logger log = LoggerFactory.getLogger(DocumentoSellaInfoService.class);

    private final DocumentoFirmaRepository documentoFirmaRepository;
    private final DocumentoFirmanteRepository documentoFirmanteRepository;
    private final DocumentoTimelineService documentoTimelineService;
    private final DocumentoPdfStamper documentoPdfStamper;
    private final DocumentoMargenLegalStamper documentoMargenLegalStamper;
    private final EnvelopeEncryptionService envelopeEncryptionService;
    private final ApplicationSigningKeyService applicationSigningKeyService;
    private final Rfc3161TimestampClient rfc3161TimestampClient;
    private final PadesSigningService padesSigningService;
    private final ApplicationEventPublisher eventPublisher;
    private final CierreCustodiaPdfAppender cierreCustodiaPdfAppender;
    private final String sitioPublicoUrl;

    public DocumentoSellaInfoService(DocumentoFirmaRepository documentoFirmaRepository,
                                      DocumentoFirmanteRepository documentoFirmanteRepository,
                                      DocumentoTimelineService documentoTimelineService,
                                      DocumentoPdfStamper documentoPdfStamper,
                                      DocumentoMargenLegalStamper documentoMargenLegalStamper,
                                      EnvelopeEncryptionService envelopeEncryptionService,
                                      ApplicationSigningKeyService applicationSigningKeyService,
                                      Rfc3161TimestampClient rfc3161TimestampClient,
                                      PadesSigningService padesSigningService,
                                      ApplicationEventPublisher eventPublisher,
                                      CierreCustodiaPdfAppender cierreCustodiaPdfAppender,
                                      @Value("${app.documento.sitio-web}") String sitioPublicoUrl) {
        this.documentoFirmaRepository = documentoFirmaRepository;
        this.documentoFirmanteRepository = documentoFirmanteRepository;
        this.documentoTimelineService = documentoTimelineService;
        this.documentoPdfStamper = documentoPdfStamper;
        this.documentoMargenLegalStamper = documentoMargenLegalStamper;
        this.envelopeEncryptionService = envelopeEncryptionService;
        this.applicationSigningKeyService = applicationSigningKeyService;
        this.rfc3161TimestampClient = rfc3161TimestampClient;
        this.padesSigningService = padesSigningService;
        this.eventPublisher = eventPublisher;
        this.cierreCustodiaPdfAppender = cierreCustodiaPdfAppender;
        this.sitioPublicoUrl = sitioPublicoUrl;
    }

    // Bug real diagnosticado en vivo (auditoria del motor de firma, 2026-11-04): "firmar se
    // queda en error, rechazar funciona". Causa -- este metodo corria en la MISMA transaccion
    // que DocumentoFirmaService#registrarFirmaDeParte, asi que cualquier fallo en el paso de
    // sellado (estampado del PDF, margen legal, firma de aplicacion) revertia TAMBIEN el
    // registro de la firma que el firmante ya habia capturado: el firmante veia un 500
    // generico y tenia que volver a firmar desde cero. registrarRechazoDeParte nunca llama a
    // este metodo, por eso rechazar nunca fallaba igual.
    //
    // REQUIRES_NEW aisla el sellado en su PROPIA transaccion: si falla, solo se revierte lo
    // que este metodo mismo alcanzo a cambiar (nunca llega a persistir un estado a medias,
    // ver el comentario de sellar() mas abajo), y el llamador (ver
    // DocumentoFirmaService#intentarSellarSinPropagarError) atrapa la excepcion sin dejarla
    // subir -- la firma del firmante queda SIEMPRE confirmada. El sellado se reintenta solo
    // despues (ver DocumentoSellaInfoRetryScheduler), igual que ya existe para el sello de
    // tiempo RFC3161 (Rfc3161RetryScheduler) y para la compuerta de staff
    // (DocumentoFirmaStaffRetryListener).
    //
    // Bug real diagnosticado en vivo (2026-11-05): antes recibia la entidad DocumentoFirma ya
    // cargada por el llamador. Como REQUIRES_NEW abre SIEMPRE una sesion/EntityManager nueva,
    // esa entidad llega DESLIGADA de esa sesion -- los setters de sellar()/remitir()/
    // encriptar()/firmarConLlaveDeAplicacion() modificaban el objeto en memoria, pero sin
    // estar la entidad gestionada por la sesion activa, Hibernate nunca detectaba esos
    // cambios para guardarlos al terminar la transaccion (ni un solo .save() explicito en
    // todo este flujo, siempre confio en el dirty-checking automatico). El resultado: la
    // operacion completaba SIN lanzar ninguna excepcion, pero el documento se quedaba
    // exactamente igual en la base de datos -- "0 de 1 avanzaron" en el reintento aunque el
    // sellado hubiera corrido entero sin fallar. Recibir el id y recargar la entidad AQUI
    // mismo garantiza que quede gestionada por ESTA sesion durante todo el metodo.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void intentarSellarYRemitir(UUID documentoId) {
        DocumentoFirma documento = documentoFirmaRepository.findById(documentoId).orElse(null);
        if (documento == null) {
            log.warn("DIAGNOSTICO 2026-11-05: documento {} no encontrado al intentar sellar.", documentoId);
            return;
        }
        List<DocumentoFirmante> firmantes = documentoFirmanteRepository.findByDocumentoIdOrderByOrdenAsc(documento.getId());
        log.warn("DIAGNOSTICO 2026-11-05: documento {} estado={} firmantes={}", documentoId,
                documento.getEstadoDocumento(),
                firmantes.stream().map(f -> f.getNombreCapturado() + "=" + f.getEstado()).toList());
        // Nunca sellar un documento donde alguna parte rechazó firmar -- RECHAZADO solo
        // cuenta como "turno cerrado" para la secuencia de firma (ver
        // SignerTokenService#esSuTurno), nunca como "firma completa" para efectos de
        // sellado. En la práctica DocumentoFirmaService#registrarRechazoDeParte ya anula el
        // documento antes de que se pueda llegar aquí, pero esta guarda se mantiene como
        // segunda capa de defensa: un documento con un rechazo nunca debe sellarse como si
        // todas las partes hubieran estado de acuerdo.
        boolean hayRechazados = firmantes.stream().anyMatch(f -> DocumentoFirmante.ESTADO_RECHAZADO.equals(f.getEstado()));
        if (hayRechazados) {
            log.warn("DIAGNOSTICO 2026-11-05: documento {} tiene firmante(s) rechazado(s), no se sella.", documentoId);
            return;
        }
        boolean hayPendientes = firmantes.stream().anyMatch(f -> !DocumentoFirmante.ESTADO_FIRMADO.equals(f.getEstado()));
        if (hayPendientes) {
            log.warn("DIAGNOSTICO 2026-11-05: documento {} todavia tiene firmante(s) pendiente(s), no se sella.", documentoId);
            return;
        }

        sellar(documento, firmantes);
        log.warn("DIAGNOSTICO 2026-11-05: documento {} selló -- estado ahora {}", documentoId, documento.getEstadoDocumento());
        remitir(documento, firmantes);
        log.warn("DIAGNOSTICO 2026-11-05: documento {} remitió -- estado ahora {}", documentoId, documento.getEstadoDocumento());
        encriptar(documento);
        log.warn("DIAGNOSTICO 2026-11-05: documento {} encriptó -- estado ahora {}", documentoId, documento.getEstadoDocumento());
        // Debe correr AQUÍ, al final, no dentro de sellar(): el mensaje canónico incluye
        // documento.getEstadoDocumento() (ver ApplicationSignatureVerifier.mensajeCanonico),
        // y remitir()/encriptar() lo cambian de nuevo (REMITIDO, luego ENCRIPTADO) DESPUÉS
        // de fijar SELLADO. Firmar antes de que el estado llegue a su valor final
        // persistido significa que cualquier verificación posterior reconstruye el mensaje
        // con ENCRIPTADO, nunca coincide con la firma calculada sobre SELLADO, y
        // ApplicationSignatureVerifier.verificar() falla SIEMPRE -- marcando como ALTERADO
        // (con alerta a gerencia) cualquier documento legítimo en cuanto alguien lo verifica.
        firmarConLlaveDeAplicacion(documento);
        log.warn("DIAGNOSTICO 2026-11-05: documento {} terminó intentarSellarYRemitir sin excepción -- estado final {}",
                documentoId, documento.getEstadoDocumento());
    }

    private void sellar(DocumentoFirma documento, List<DocumentoFirmante> firmantes) {
        byte[] pdfConFirmas = documentoPdfStamper.estamparFirmas(documento.getContenidoPdf(), documento, firmantes);
        // Aviso legal en el margen de CADA página (originales + páginas de firma), antes de
        // la firma PAdES para que quede cubierto por ella -- ver DocumentoMargenLegalStamper.
        byte[] pdfConMargenLegal = documentoMargenLegalStamper.estamparMargen(pdfConFirmas, documento.getCodigoVerificacion());
        documento.setContenidoPdf(pdfConMargenLegal);

        firmarDigitalmente(documento);
        documento.setEstadoDocumento(DocumentoFirma.ESTADO_DOC_SELLADO);
        sellarTiempo(documento);
        documentoTimelineService.registrarSistema(documento, DocumentoEvento.TIPO_SELLADO,
                "Documento sellado: todos los firmantes completaron su firma.");
    }

    // Aplica la firma digital PAdES/PKCS7 embebida sobre el contenido YA ESTAMPADO (ver
    // PadesSigningService). Debe correr AQUÍ, justo después de generar el contenido final y
    // ANTES de fijar ESTADO_DOC_SELLADO/sellarTiempo(): hash_contenido hay que
    // recalcularlo sobre el resultado FINAL antes de que el sello de tiempo RFC 3161 y la
    // firma de aplicación Ed25519 (que dependen de ese hash) se calculen.
    private void firmarDigitalmente(DocumentoFirma documento) {
        String razon = "Sellado por el motor de firma electrónica de SIE Jurídicos";
        String ubicacion = "Bogotá D.C., Colombia";
        byte[] pdfFinal = padesSigningService.firmarPdf(documento.getContenidoPdf(), razon, ubicacion)
                .map(PadesSigningService.ResultadoFirmaPades::pdfFirmado)
                .orElse(documento.getContenidoPdf());

        documento.setContenidoPdf(pdfFinal);
        documento.setHashContenido(HashUtil.sha256Hex(pdfFinal));
    }

    // Sellado de tiempo RFC 3161 sobre el hash del PDF sellado: un fallo de la TSA externa
    // deja constancia informativa en el timeline, pero jamás bloquea el sellado del documento.
    private void sellarTiempo(DocumentoFirma documento) {
        rfc3161TimestampClient.sellar(HashUtil.hexToBytes(documento.getHashContenido())).ifPresentOrElse(
                sello -> {
                    documento.setRfc3161Token(sello.token());
                    documento.setRfc3161UrlTsa(sello.urlTsa());
                    documento.setRfc3161SelladoEn(sello.selladoEn());
                    documentoTimelineService.registrarSistema(documento, DocumentoEvento.TIPO_SELLO_TIEMPO_APLICADO,
                            "Sello de tiempo RFC 3161 obtenido de " + sello.urlTsa() + ".");
                },
                () -> documentoTimelineService.registrarSistema(documento, DocumentoEvento.TIPO_SELLO_TIEMPO_FALLIDO,
                        "No fue posible obtener un sello de tiempo RFC 3161 externo (TSA no disponible)."));
    }

    // Firma criptográfica ASIMÉTRICA propia (Ed25519), además del HMAC ya existente
    // (CodigoVerificacionCalculator): refuerza la evidencia de integridad, no la sustituye.
    // Debe correr DESPUÉS de fijar hashContenido/estadoDocumento definitivos, porque ambos
    // forman parte del mensaje firmado (ver ApplicationSignatureVerifier).
    private void firmarConLlaveDeAplicacion(DocumentoFirma documento) {
        documento.setFirmaAppGeneradaEn(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS));
        String mensaje = ApplicationSignatureVerifier.mensajeCanonico(documento);
        ApplicationSigningKeyService.FirmaResultado resultado =
                applicationSigningKeyService.firmar(mensaje.getBytes(StandardCharsets.UTF_8));
        documento.setFirmaAppValor(resultado.firma());
        documento.setFirmaAppKid(resultado.kid());
    }

    private void remitir(DocumentoFirma documento, List<DocumentoFirmante> firmantes) {
        documento.setEstadoDocumento(DocumentoFirma.ESTADO_DOC_REMITIDO);
        documentoTimelineService.registrarSistema(documento, DocumentoEvento.TIPO_REMITIDO,
                "Documento sellado remitido a todas las partes.");
        for (DocumentoFirmante firmante : firmantes) {
            if (firmante.esParteExterna()) {
                eventPublisher.publishEvent(new DocumentoRemitidoEvent(firmante, documento.getContenidoPdf()));
            }
        }
    }

    private void encriptar(DocumentoFirma documento) {
        EnvelopeEncryptionService.ResultadoCifrado resultado = envelopeEncryptionService.cifrar(documento.getContenidoPdf());
        documento.setContenidoPdf(resultado.contenidoCifrado());
        documento.setContenidoIv(resultado.contenidoIv());
        documento.setDekCifrado(resultado.dekCifrado());
        documento.setDekIv(resultado.dekIv());
        documento.setKekId(resultado.kekId());
        documento.setContenidoCifrado(true);
        documento.setEstadoDocumento(DocumentoFirma.ESTADO_DOC_ENCRIPTADO);
        documentoTimelineService.registrarSistema(documento, DocumentoEvento.TIPO_ENCRIPTADO,
                "Documento cifrado en reposo (AES-256-GCM, envelope encryption).");
    }

    // Genera la Hoja de Cierre de Custodia y la anexa COMO COPIA, sin tocar ni persistir el
    // documento real: para que un staff autenticado la vea solo en SU descarga manual
    // puntual, nunca en lo que se remite automáticamente a las partes externas (ver
    // DocumentoSellaInfoService.remitir(), 2026-10-04: evita que cada firmante descargue
    // los datos forenses de los DEMÁS firmantes del mismo documento). Si el documento no
    // tiene firmantes, no hay nada que anexar y se devuelve el contenido tal cual.
    @Transactional(readOnly = true)
    public byte[] anexarHojaCierreParaDescargaManual(DocumentoFirma documento, byte[] contenidoDescifrado) {
        List<DocumentoFirmante> firmantes = documentoFirmanteRepository.findByDocumentoIdOrderByOrdenAsc(documento.getId());
        if (firmantes.isEmpty()) {
            return contenidoDescifrado;
        }
        HojaCierreCustodiaData datosCierre = construirDatosCierre(documento, firmantes);
        return cierreCustodiaPdfAppender.anexar(contenidoDescifrado, datosCierre);
    }

    private HojaCierreCustodiaData construirDatosCierre(DocumentoFirma documento, List<DocumentoFirmante> firmantes) {
        List<HojaCierreCustodiaData.FirmanteResumen> firmantesResumen = firmantes.stream()
                .map(f -> new HojaCierreCustodiaData.FirmanteResumen(
                        f.getNombreCapturado(), f.getTipoFirmante(), f.getDocumentoIdentidadCapturado(),
                        f.getFirmadoEn(), f.getIpCaptura(), f.getUserAgentCaptura(), f.getSenalesProxyDetectadas(),
                        f.getHashEvidencia(), f.getFotoVerificacionHumana()))
                .toList();
        String urlVerificacion = documento.getCodigoVerificacion() != null
                ? sitioPublicoUrl + "/verificar-documento?codigo=" + documento.getCodigoVerificacion()
                : null;
        return new HojaCierreCustodiaData(
                documento.getNombre(),
                documento.getCodigoVerificacion(),
                urlVerificacion,
                LocalDateTime.now(),
                firmantesResumen);
    }
}
