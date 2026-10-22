package sie.siejuridicos.documento;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sie.siejuridicos.common.exception.EntidadInvalidaException;
import sie.siejuridicos.crm.ClienteCrm;
import sie.siejuridicos.crm.TipoClienteCrm;
import sie.siejuridicos.documento.events.DocumentoFirmaRechazadaEvent;
import sie.siejuridicos.documento.events.DocumentoListoParaFirmarEvent;
import sie.siejuridicos.documento.pdf.SignatureImageProcessor;
import sie.siejuridicos.usuario.UsuarioInterno;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

// Orquestador del motor de firma electrónica de SIE. A diferencia del motor de referencia
// (UCN, que resuelve dinámicamente "cuál conciliador y cuál director firman" porque cada
// caso tiene exactamente un firmante de cada rol fijo), aquí gerencia arma explícitamente
// la lista completa de firmantes al emitir el documento -- gerente + cada abogado asignado
// + cada parte elegida de la biblioteca de contactos -- y este servicio crea TODAS esas
// filas de una sola vez (ver crearFirmantesIniciales), en el orden en que deben firmar
// (gerente y abogados primero, partes después, en el orden en que gerencia las agregó).
@Service
public class DocumentoFirmaService {

    // Pedido explícito del usuario: si la parte es una EMPRESA, el canal queda forzado a
    // correo (nunca WhatsApp); si es una persona natural, gerencia elige. Se valida aquí,
    // en el servidor, no solo en el formulario del panel.
    // BUG REAL encontrado en la auditoría en vivo del motor: esto lanzaba
    // IllegalArgumentException, que el manejador global de excepciones no traduce a una
    // respuesta 4xx -- el admin recibía un 500 "Ocurrió un error inesperado" genérico en vez
    // del mensaje real, y quedaba un stack trace de ruido en los logs por lo que en realidad
    // es una validación de negocio normal. EntidadInvalidaException sí tiene manejador
    // dedicado (ver el resto de validaciones de DocumentoAdminService, mismo patrón).
    public record ParteAFirmar(ClienteCrm clienteCrm, String canal) {
        public ParteAFirmar {
            if (clienteCrm.getTipo() == TipoClienteCrm.EMPRESA && !DocumentoFirmante.CANAL_EMAIL.equals(canal)) {
                throw new EntidadInvalidaException(
                        "Las partes que son empresa solo pueden recibir el enlace de firma por correo.");
            }
        }
    }

    private final DocumentoAutoFirmanteStaff documentoAutoFirmanteStaff;
    private final DocumentoTimelineService documentoTimelineService;
    private final DocumentoFirmanteRepository documentoFirmanteRepository;
    private final SignerTokenService signerTokenService;
    private final DocumentoSellaInfoService documentoSellaInfoService;
    private final EvidenceCaptureService evidenceCaptureService;
    private final Rfc3161TimestampClient rfc3161TimestampClient;
    private final ApplicationEventPublisher eventPublisher;
    private final String sitioPublicoUrl;
    private final EnvelopeEncryptionService envelopeEncryptionService;

    public DocumentoFirmaService(DocumentoAutoFirmanteStaff documentoAutoFirmanteStaff,
                                  DocumentoTimelineService documentoTimelineService,
                                  DocumentoFirmanteRepository documentoFirmanteRepository,
                                  SignerTokenService signerTokenService,
                                  DocumentoSellaInfoService documentoSellaInfoService,
                                  EvidenceCaptureService evidenceCaptureService,
                                  Rfc3161TimestampClient rfc3161TimestampClient,
                                  ApplicationEventPublisher eventPublisher,
                                  @Value("${app.documento.sitio-web}") String sitioPublicoUrl,
                                  EnvelopeEncryptionService envelopeEncryptionService) {
        this.documentoAutoFirmanteStaff = documentoAutoFirmanteStaff;
        this.documentoTimelineService = documentoTimelineService;
        this.documentoFirmanteRepository = documentoFirmanteRepository;
        this.signerTokenService = signerTokenService;
        this.documentoSellaInfoService = documentoSellaInfoService;
        this.evidenceCaptureService = evidenceCaptureService;
        this.rfc3161TimestampClient = rfc3161TimestampClient;
        this.eventPublisher = eventPublisher;
        this.sitioPublicoUrl = sitioPublicoUrl;
        this.envelopeEncryptionService = envelopeEncryptionService;
    }

    // Único punto de entrada para arrancar el proceso completo: crea el firmante del
    // gerente, uno por cada abogado asignado y uno por cada parte elegida (en ese orden),
    // marca el documento EMITIDO y de inmediato intenta cerrar la compuerta de firma de
    // staff. Debe llamarse dentro de la misma transacción de negocio que creó el
    // DocumentoFirma con su número de secuencia ya asignado (ver DocumentoAdminController).
    @Transactional(propagation = Propagation.MANDATORY)
    public void procesarEmision(DocumentoFirma documento, UsuarioInterno gerente, List<UsuarioInterno> abogados,
                                 List<ParteAFirmar> partes) {
        crearFirmantesIniciales(documento, gerente, abogados, partes);
        documentoTimelineService.registrarSistema(documento, DocumentoEvento.TIPO_EMITIDO,
                "Documento '" + documento.getNombre() + "' emitido con número de secuencia " + documento.getNumeroSecuencia() + ".");
        documento.setEstadoDocumento(DocumentoFirma.ESTADO_DOC_EMITIDO);
        intentarFirmarYSellar(documento);
    }

    private void crearFirmantesIniciales(DocumentoFirma documento, UsuarioInterno gerente,
                                          List<UsuarioInterno> abogados, List<ParteAFirmar> partes) {
        int orden = 0;
        documentoFirmanteRepository.save(new DocumentoFirmante(documento, DocumentoFirmante.TIPO_STAFF_GERENTE,
                gerente, null, null, gerente.getNombre(), null, orden++));
        for (UsuarioInterno abogado : abogados) {
            documentoFirmanteRepository.save(new DocumentoFirmante(documento, DocumentoFirmante.TIPO_STAFF_ABOGADO,
                    abogado, null, null, abogado.getNombre(), null, orden++));
        }
        for (ParteAFirmar parte : partes) {
            DocumentoFirmante firmante = new DocumentoFirmante(documento, DocumentoFirmante.TIPO_PARTE, null,
                    parte.clienteCrm(), parte.clienteCrm().getTipo().name(), parte.clienteCrm().getNombre(),
                    parte.clienteCrm().getCedulaNit(), orden++);
            firmante.setCanalNotificacion(parte.canal());
            firmante.setDestinoNotificacion(DocumentoFirmante.CANAL_EMAIL.equals(parte.canal())
                    ? parte.clienteCrm().getCorreo() : parte.clienteCrm().getTelefono());
            documentoFirmanteRepository.save(firmante);
        }
    }

    // Gate obligatorio de firmas de staff: ningún documento se sella sin la firma
    // manuscrita del gerente Y de cada abogado asignado. Reentrante e idempotente: si el
    // documento ya no está EMITIDO (ya se firmó antes, o fue anulado) no hace nada, así
    // que el reintento disparado al cargar una firma de staff (ver
    // DocumentoFirmaStaffRetryListener) puede recorrer todos los documentos atascados sin
    // riesgo de firmarlos dos veces. Retorna true si el documento avanzó a FIRMADO_STAFF.
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean intentarFirmarYSellar(DocumentoFirma documento) {
        if (!DocumentoFirma.ESTADO_DOC_EMITIDO.equals(documento.getEstadoDocumento())) {
            return false;
        }
        List<DocumentoFirmante> todos = documentoFirmanteRepository.findByDocumentoIdOrderByOrdenAsc(documento.getId());
        List<DocumentoFirmante> staffPendientes = todos.stream()
                .filter(DocumentoFirmante::esStaff)
                .filter(f -> DocumentoFirmante.ESTADO_PENDIENTE.equals(f.getEstado()))
                .toList();

        List<String> faltantes = new ArrayList<>();
        for (DocumentoFirmante f : staffPendientes) {
            if (f.getStaff().getFirmaPng() == null) {
                String rol = DocumentoFirmante.TIPO_STAFF_GERENTE.equals(f.getTipoFirmante()) ? "gerente" : "abogado";
                faltantes.add(f.getStaff().getNombre() + " (" + rol + ") aún no ha cargado su firma");
            }
        }
        if (!faltantes.isEmpty()) {
            documentoTimelineService.registrarSistema(documento, DocumentoEvento.TIPO_EMITIDO,
                    "Firma de staff pendiente: " + String.join("; ", faltantes)
                            + ". El documento queda emitido, sin sellar, hasta que se cargue la firma faltante.");
            return false;
        }

        for (DocumentoFirmante f : staffPendientes) {
            documentoAutoFirmanteStaff.firmar(f);
        }
        documento.setEstadoDocumento(DocumentoFirma.ESTADO_DOC_FIRMADO_STAFF);

        List<DocumentoFirmante> partes = todos.stream().filter(DocumentoFirmante::esParteExterna).toList();
        if (!partes.isEmpty()) {
            iniciarFirmaDePartes(documento, partes);
        } else {
            documentoSellaInfoService.intentarSellarYRemitir(documento);
        }
        return true;
    }

    private void iniciarFirmaDePartes(DocumentoFirma documento, List<DocumentoFirmante> partes) {
        // Firma secuencial obligatoria: solo se emite el enlace del primero en el orden;
        // los demás siguen PENDIENTE (sin token) hasta que firme el anterior (ver
        // emitirEnlaceDeSiguientes).
        List<DocumentoFirmante> conTurno = partes.stream().filter(signerTokenService::esSuTurno).toList();
        documento.setEstadoDocumento(DocumentoFirma.ESTADO_DOC_NOTIFICADO);
        documentoTimelineService.registrarSistema(documento, DocumentoEvento.TIPO_NOTIFICADO,
                "Se generó el enlace de firma para " + conTurno.size() + " de " + partes.size()
                        + " parte(s); las demás firmarán en orden, cada una tras la anterior.");

        for (DocumentoFirmante firmante : conTurno) {
            emitirYNotificar(firmante);
        }

        documento.setEstadoDocumento(DocumentoFirma.ESTADO_DOC_EN_FIRMA_PARTES);
        documentoTimelineService.registrarSistema(documento, DocumentoEvento.TIPO_EN_FIRMA_PARTES_INICIADO,
                "Quedan " + partes.size() + " parte(s) pendientes de firmar en línea.");
    }

    private void emitirYNotificar(DocumentoFirmante firmante) {
        String tokenPlano = signerTokenService.emitirToken(firmante);
        firmante.setEstado(DocumentoFirmante.ESTADO_NOTIFICADO);
        documentoFirmanteRepository.save(firmante);
        eventPublisher.publishEvent(new DocumentoListoParaFirmarEvent(firmante, sitioPublicoUrl + "/firmar/" + tokenPlano));
    }

    // Tras resolverse un firmante, emite el enlace de quienes ya tienen turno y aún no lo
    // recibieron (PENDIENTE sin token). Deja el evento NOTIFICADO en la cadena por cada uno.
    void emitirEnlaceDeSiguientes(DocumentoFirma documento) {
        for (DocumentoFirmante candidato : documentoFirmanteRepository.findByDocumentoIdOrderByOrdenAsc(documento.getId())) {
            if (DocumentoFirmante.ESTADO_PENDIENTE.equals(candidato.getEstado()) && candidato.getTokenHash() == null
                    && candidato.esParteExterna() && signerTokenService.esSuTurno(candidato)) {
                emitirYNotificar(candidato);
                documentoTimelineService.registrar(documento, DocumentoEvento.TIPO_NOTIFICADO, DocumentoEvento.ACTOR_SISTEMA,
                        null, candidato, "Es el turno de " + candidato.getNombreCapturado() + ": se le envió su enlace de firma.",
                        null, null);
            }
        }
    }

    // Llamado por el flujo público (PublicSignatureController) cuando una parte externa
    // termina de capturar su firma en pantalla: cierra su paquete de evidencia, marca su
    // firma como completa y, si era la última pendiente, dispara el sellado del documento.
    @Transactional
    public void registrarFirmaDeParte(DocumentoFirmante firmante, byte[] firmaPngCapturada, String trazoVectorialJson) {
        DocumentoFirma documento = firmante.getDocumento();
        // Recorte automático al contenido real UNA SOLA VEZ, aquí mismo, antes de calcular
        // el hash de evidencia y de guardar: así el hash cubre exactamente la imagen que
        // después se estampa en el PDF (ver DocumentoPdfStamper) y una firma dibujada en
        // una esquina de un canvas grande no se ve minúscula al escalarla.
        byte[] firmaPng = SignatureImageProcessor.recortarYEscalar(firmaPngCapturada, 1.0f, 640, 240);
        String hashEvidencia = evidenceCaptureService.calcularHashEvidencia(firmante, firmaPng, documento.getHashContenido());
        firmante.registrarFirma(firmaPng, hashEvidencia);
        registrarTrazoVectorial(firmante, trazoVectorialJson);
        rfc3161TimestampClient.sellar(HashUtil.hexToBytes(hashEvidencia))
                .ifPresent(sello -> firmante.registrarSelloTiempo(sello.token(), sello.urlTsa()));
        documentoFirmanteRepository.save(firmante);
        documentoTimelineService.registrar(documento, DocumentoEvento.TIPO_FIRMADO_POR_PARTE, DocumentoEvento.ACTOR_PARTE,
                null, firmante, firmante.getNombreCapturado() + " firmó el documento en línea.",
                firmante.getIpCaptura(), firmante.getUserAgentCaptura());
        emitirEnlaceDeSiguientes(documento);
        documentoSellaInfoService.intentarSellarYRemitir(documento);
    }

    // Llamado por el flujo público cuando una parte declina firmar. A diferencia de
    // registrarFirmaDeParte(), NUNCA llama a documentoSellaInfoService -- un documento con
    // una parte que rechazó firmar no puede quedar válidamente ejecutado. El proceso entero
    // se anula de inmediato: se revoca el enlace de cualquier otro firmante que todavía
    // estuviera pendiente (para que nadie pueda seguir firmando un documento ya muerto) y
    // se alerta a gerencia.
    @Transactional
    public void registrarRechazoDeParte(DocumentoFirmante firmante, String motivo, String ip, String userAgent) {
        DocumentoFirma documento = firmante.getDocumento();
        firmante.registrarRechazo(motivo);
        firmante.registrarCapturaTecnica(ip, userAgent);
        documentoFirmanteRepository.save(firmante);
        documentoTimelineService.registrar(documento, DocumentoEvento.TIPO_FIRMA_RECHAZADA, DocumentoEvento.ACTOR_PARTE,
                null, firmante, firmante.getNombreCapturado() + " rechazó firmar el documento. Motivo: "
                        + (motivo == null || motivo.isBlank() ? "no indicado." : motivo),
                ip, userAgent);

        for (DocumentoFirmante otro : documentoFirmanteRepository.findByDocumentoIdOrderByOrdenAsc(documento.getId())) {
            if (otro.getId().equals(firmante.getId())
                    || DocumentoFirmante.ESTADO_FIRMADO.equals(otro.getEstado())
                    || DocumentoFirmante.ESTADO_RECHAZADO.equals(otro.getEstado())) {
                continue;
            }
            signerTokenService.revocar(otro);
            otro.setEstado(DocumentoFirmante.ESTADO_REVOCADO);
            documentoFirmanteRepository.save(otro);
        }

        documento.setEstadoDocumento(DocumentoFirma.ESTADO_DOC_ANULADO);
        documentoTimelineService.registrarSistema(documento, DocumentoEvento.TIPO_PROCESO_ANULADO,
                "El proceso de firma se anuló: " + firmante.getNombreCapturado() + " rechazó firmar.");
        eventPublisher.publishEvent(new DocumentoFirmaRechazadaEvent(documento, firmante.getNombreCapturado(), motivo));
    }

    // Cancelación manual por gerencia (pedido explícito del usuario, ver
    // DocumentoAdminController#cancelar): a diferencia de registrarRechazoDeParte(), nadie
    // rechazó firmar -- gerencia simplemente decide no seguir adelante con el proceso (se
    // subió el documento equivocado, se canceló el trámite, etc.). Mismo efecto sobre los
    // firmantes (revoca cualquier enlace todavía pendiente) pero con su PROPIO estado
    // terminal (CANCELADO, nunca ANULADO) para que el historial distinga claramente ambas
    // causas. Solo tiene sentido antes de que el documento quede sellado: una vez
    // ENCRIPTADO ya es un documento legal completo, cancelarlo no tendría efecto real sobre
    // lo ya firmado (para eso existe el sistema de solicitudes de cambio con doble
    // confirmación, ver DocumentChangeRequestService).
    @Transactional
    public void cancelar(DocumentoFirma documento, UsuarioInterno staff, String motivo) {
        if (DocumentoFirma.ESTADO_DOC_ENCRIPTADO.equals(documento.getEstadoDocumento())
                || DocumentoFirma.ESTADO_DOC_ANULADO.equals(documento.getEstadoDocumento())
                || DocumentoFirma.ESTADO_DOC_CANCELADO.equals(documento.getEstadoDocumento())) {
            throw new EntidadInvalidaException(
                    "Este documento ya no se puede cancelar (estado actual: " + documento.getEstadoDocumento() + ").");
        }

        for (DocumentoFirmante firmante : documentoFirmanteRepository.findByDocumentoIdOrderByOrdenAsc(documento.getId())) {
            if (DocumentoFirmante.ESTADO_FIRMADO.equals(firmante.getEstado())
                    || DocumentoFirmante.ESTADO_RECHAZADO.equals(firmante.getEstado())) {
                continue;
            }
            signerTokenService.revocar(firmante);
            firmante.setEstado(DocumentoFirmante.ESTADO_REVOCADO);
            documentoFirmanteRepository.save(firmante);
        }

        documento.setEstadoDocumento(DocumentoFirma.ESTADO_DOC_CANCELADO);
        documentoTimelineService.registrarStaff(documento, DocumentoEvento.TIPO_PROCESO_CANCELADO, staff,
                "Gerencia canceló el proceso de firma. Motivo: " + (motivo == null || motivo.isBlank() ? "no indicado." : motivo));
    }

    // Biometría dinámica del trazo (puntos x/y/tiempo/presión): un dato más sensible que
    // la imagen final de la firma, así que se cifra en reposo con el mismo esquema
    // envelope encryption AES-256-GCM que DocumentoFirma.contenidoPdf -- nunca se expone
    // en claro por ningún endpoint. Si el frontend no lo envía (navegador viejo sin
    // soporte de PointerEvent.pressure), simplemente no queda nada guardado: no bloquea
    // la firma, es evidencia ADICIONAL a la imagen, no un requisito.
    private void registrarTrazoVectorial(DocumentoFirmante firmante, String trazoVectorialJson) {
        if (trazoVectorialJson == null || trazoVectorialJson.isBlank()) {
            return;
        }
        EnvelopeEncryptionService.ResultadoCifrado cifrado =
                envelopeEncryptionService.cifrar(trazoVectorialJson.getBytes(StandardCharsets.UTF_8));
        firmante.registrarTrazoVectorial(cifrado.contenidoCifrado(), cifrado.contenidoIv(),
                cifrado.dekCifrado(), cifrado.dekIv(), cifrado.kekId());
    }
}
