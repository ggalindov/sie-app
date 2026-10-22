package sie.siejuridicos.documento;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sie.siejuridicos.caso.Caso;
import sie.siejuridicos.caso.CasoRepository;
import sie.siejuridicos.common.exception.EntidadInvalidaException;
import sie.siejuridicos.common.exception.RecursoNoEncontradoException;
import sie.siejuridicos.crm.ClienteCrm;
import sie.siejuridicos.crm.ClienteCrmRepository;
import sie.siejuridicos.documento.dto.AsignarParteRequest;
import sie.siejuridicos.documento.dto.DocumentoFirmaListadoDto;
import sie.siejuridicos.documento.dto.DocumentoSeguimientoDto;
import sie.siejuridicos.usuario.RolUsuario;
import sie.siejuridicos.usuario.UsuarioInterno;
import sie.siejuridicos.usuario.UsuarioInternoRepository;

import java.util.List;
import java.util.UUID;

// API de administración del motor de firma -- pieza NUEVA, no existe en el motor de
// referencia (que recibe sus firmantes ya resueltos desde el flujo de audiencias de UCN).
// Aquí gerencia arma la solicitud completa en un solo paso: sube el documento, elige los
// abogados que deben firmar y las partes de la biblioteca de contactos (ClienteCrm) que
// deben firmar, con el canal de notificación de cada una.
@Service
public class DocumentoAdminService {

    private static final byte[] PDF_MAGIC = {'%', 'P', 'D', 'F', '-'};
    private static final long TAMANO_MAXIMO_PDF_BYTES = 20L * 1024 * 1024;

    private final DocumentoFirmaRepository documentoFirmaRepository;
    private final DocumentoFirmanteRepository documentoFirmanteRepository;
    private final DocumentoEventoRepository documentoEventoRepository;
    private final DocumentoTimelineService documentoTimelineService;
    private final DocumentoEventoChainVerifier documentoEventoChainVerifier;
    private final DocumentoFirmaService documentoFirmaService;
    private final DocumentoSellaInfoService documentoSellaInfoService;
    private final EvidenceCertificateService evidenceCertificateService;
    private final EnvelopeEncryptionService envelopeEncryptionService;
    private final CodigoVerificacionCalculator codigoVerificacionCalculator;
    private final UsuarioInternoRepository usuarioInternoRepository;
    private final ClienteCrmRepository clienteCrmRepository;
    private final CasoRepository casoRepository;

    public DocumentoAdminService(DocumentoFirmaRepository documentoFirmaRepository,
                                  DocumentoFirmanteRepository documentoFirmanteRepository,
                                  DocumentoEventoRepository documentoEventoRepository,
                                  DocumentoTimelineService documentoTimelineService,
                                  DocumentoEventoChainVerifier documentoEventoChainVerifier,
                                  DocumentoFirmaService documentoFirmaService,
                                  DocumentoSellaInfoService documentoSellaInfoService,
                                  EvidenceCertificateService evidenceCertificateService,
                                  EnvelopeEncryptionService envelopeEncryptionService,
                                  CodigoVerificacionCalculator codigoVerificacionCalculator,
                                  UsuarioInternoRepository usuarioInternoRepository,
                                  ClienteCrmRepository clienteCrmRepository,
                                  CasoRepository casoRepository) {
        this.documentoFirmaRepository = documentoFirmaRepository;
        this.documentoFirmanteRepository = documentoFirmanteRepository;
        this.documentoEventoRepository = documentoEventoRepository;
        this.documentoTimelineService = documentoTimelineService;
        this.documentoEventoChainVerifier = documentoEventoChainVerifier;
        this.documentoFirmaService = documentoFirmaService;
        this.documentoSellaInfoService = documentoSellaInfoService;
        this.evidenceCertificateService = evidenceCertificateService;
        this.envelopeEncryptionService = envelopeEncryptionService;
        this.codigoVerificacionCalculator = codigoVerificacionCalculator;
        this.usuarioInternoRepository = usuarioInternoRepository;
        this.clienteCrmRepository = clienteCrmRepository;
        this.casoRepository = casoRepository;
    }

    @Transactional
    public DocumentoFirmaListadoDto crear(byte[] pdf, String nombre, Long casoId, List<Long> abogadoIds,
                                           List<AsignarParteRequest> partes, Long gerenteId) {
        validarPdf(pdf);
        if (nombre == null || nombre.isBlank()) {
            throw new EntidadInvalidaException("El documento necesita un nombre.");
        }

        UsuarioInterno gerente = usuarioInternoRepository.findById(gerenteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró el usuario autenticado."));

        Caso caso = null;
        if (casoId != null) {
            caso = casoRepository.findById(casoId)
                    .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró el caso indicado."));
        }

        List<UsuarioInterno> abogados = (abogadoIds == null ? List.<Long>of() : abogadoIds).stream()
                .map(id -> {
                    UsuarioInterno u = usuarioInternoRepository.findById(id)
                            .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró el usuario interno " + id + "."));
                    if (u.getRol() != RolUsuario.ABOGADO) {
                        throw new EntidadInvalidaException(u.getNombre() + " no tiene rol de abogado.");
                    }
                    if (!u.isActivo()) {
                        throw new EntidadInvalidaException(u.getNombre() + " no está activo.");
                    }
                    return u;
                })
                .toList();

        List<DocumentoFirmaService.ParteAFirmar> partesAFirmar = (partes == null ? List.<AsignarParteRequest>of() : partes).stream()
                .map(p -> {
                    ClienteCrm cliente = clienteCrmRepository.findById(p.clienteCrmId())
                            .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró el contacto " + p.clienteCrmId() + "."));
                    String canal = p.canal();
                    if (!DocumentoFirmante.CANAL_EMAIL.equals(canal) && !DocumentoFirmante.CANAL_WHATSAPP.equals(canal)) {
                        throw new EntidadInvalidaException("Canal de notificación inválido para " + cliente.getNombre() + ".");
                    }
                    String destino = DocumentoFirmante.CANAL_EMAIL.equals(canal) ? cliente.getCorreo() : cliente.getTelefono();
                    if (destino == null || destino.isBlank()) {
                        throw new EntidadInvalidaException(cliente.getNombre() + " no tiene "
                                + (DocumentoFirmante.CANAL_EMAIL.equals(canal) ? "correo" : "teléfono") + " registrado.");
                    }
                    // El documento final SIEMPRE se entrega por correo al terminar (ver
                    // DocumentoNotificacionListener), sin importar el canal del enlace en
                    // vivo -- por eso el correo es obligatorio para TODAS las partes, no
                    // solo para las que eligieron canal EMAIL.
                    if (cliente.getCorreo() == null || cliente.getCorreo().isBlank()) {
                        throw new EntidadInvalidaException(cliente.getNombre()
                                + " necesita un correo registrado para poder recibir el documento final firmado.");
                    }
                    return new DocumentoFirmaService.ParteAFirmar(cliente, canal);
                })
                .toList();

        DocumentoFirma documento = new DocumentoFirma(caso, gerente, nombre.trim(), "application/pdf", pdf);
        documento = documentoFirmaRepository.save(documento);

        documentoTimelineService.registrarStaff(documento, DocumentoEvento.TIPO_DILIGENCIADO, gerente,
                "Documento '" + documento.getNombre() + "' subido por " + gerente.getNombre() + ".");

        long numeroSecuencia = documentoFirmaRepository.siguienteNumeroSecuencia();
        documento.setNumeroSecuencia(numeroSecuencia);
        documento.setCodigoVerificacion(codigoVerificacionCalculator.calcular(documento.getId(), numeroSecuencia));
        documento.setHashContenido(HashUtil.sha256Hex(pdf));
        documento = documentoFirmaRepository.save(documento);

        documentoFirmaService.procesarEmision(documento, gerente, abogados, partesAFirmar);

        return DocumentoFirmaListadoDto.from(documento);
    }

    private void validarPdf(byte[] pdf) {
        if (pdf == null || pdf.length == 0) {
            throw new EntidadInvalidaException("El archivo está vacío.");
        }
        if (pdf.length > TAMANO_MAXIMO_PDF_BYTES) {
            throw new EntidadInvalidaException("El archivo supera el tamaño máximo permitido (20MB).");
        }
        if (pdf.length < PDF_MAGIC.length) {
            throw new EntidadInvalidaException("El archivo no es un PDF válido.");
        }
        for (int i = 0; i < PDF_MAGIC.length; i++) {
            if (pdf[i] != PDF_MAGIC[i]) {
                throw new EntidadInvalidaException("El archivo no es un PDF válido.");
            }
        }
    }

    @Transactional(readOnly = true)
    public List<DocumentoFirmaListadoDto> listar(String estado) {
        List<DocumentoFirma> documentos = (estado == null || estado.isBlank())
                ? documentoFirmaRepository.findTop200ByOrderByFechaCreacionDesc()
                : documentoFirmaRepository.findByEstadoDocumento(estado);
        return documentos.stream().map(DocumentoFirmaListadoDto::from).toList();
    }

    @Transactional(readOnly = true)
    public DocumentoSeguimientoDto seguimiento(UUID documentoId) {
        DocumentoFirma documento = obtener(documentoId);
        List<DocumentoFirmante> firmantes = documentoFirmanteRepository.findByDocumentoIdOrderByOrdenAsc(documentoId);
        List<DocumentoEvento> eventos = documentoEventoRepository.findByDocumentoIdOrderBySecuenciaAsc(documentoId);
        ResultadoVerificacionCadena cadena = documentoEventoChainVerifier.verificar(documento);
        return DocumentoSeguimientoDto.from(documento, firmantes, eventos, cadena);
    }

    @Transactional(readOnly = true)
    public byte[] descargar(UUID documentoId, boolean conHojaCierre) {
        DocumentoFirma documento = obtener(documentoId);
        byte[] contenido = envelopeEncryptionService.leerContenido(documento);
        if (!conHojaCierre) {
            return contenido;
        }
        return documentoSellaInfoService.anexarHojaCierreParaDescargaManual(documento, contenido);
    }

    @Transactional(readOnly = true)
    public byte[] certificadoEvidencia(UUID documentoId) {
        return evidenceCertificateService.generarConsolidadoPorDocumento(documentoId);
    }

    private DocumentoFirma obtener(UUID id) {
        return documentoFirmaRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No se encontró el documento solicitado."));
    }
}
