package sie.siejuridicos.documento;

import com.lowagie.text.pdf.PdfReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sie.siejuridicos.documento.dto.VerificacionPublicaResponse;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

// Portal público de verificación anti-alteración. Reutiliza las mismas capas de evidencia
// que ya usan la verificación por código y el certificado de evidencia -cadena de hashes
// de eventos (DocumentoEventoChainVerifier), firma de aplicación Ed25519
// (ApplicationSignatureVerifier), puerta de integridad (DocumentIntegrityGuard) y firma
// PAdES (PadesInspector)- y las expone sin autenticación con veredicto
// INTEGRO / ALTERADO / NO_REGISTRADO.
//
// Decisión de diseño: una consulta pública NUNCA marca ALTERADO un documento por el solo
// hecho de que alguien suba un archivo distinto (cualquiera podría envenenar el estado de
// un documento auténtico subiendo una copia manipulada). Lo que sí se marca es lo que
// revela manipulación del propio REGISTRO de SIE (firma de aplicación inválida, cadena de
// eventos rota). Cada consulta que identifica un documento agrega un evento
// VERIFICACION_CONSULTADA a su cadena de hashes.
@Service
public class VerificacionPublicaService {

    private static final String MARCA_CODIGO = "verificacion:";
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final DocumentoFirmaRepository documentoRepository;
    private final DocumentoVersionRepository versionRepository;
    private final DocumentoFirmanteRepository firmanteRepository;
    private final DocumentoEventoChainVerifier chainVerifier;
    private final ApplicationSignatureVerifier applicationSignatureVerifier;
    private final DocumentIntegrityGuard integrityGuard;
    private final DocumentoTimelineService timelineService;
    private final PadesInspector padesInspector;

    public VerificacionPublicaService(DocumentoFirmaRepository documentoRepository,
                                      DocumentoVersionRepository versionRepository,
                                      DocumentoFirmanteRepository firmanteRepository,
                                      DocumentoEventoChainVerifier chainVerifier,
                                      ApplicationSignatureVerifier applicationSignatureVerifier,
                                      DocumentIntegrityGuard integrityGuard,
                                      DocumentoTimelineService timelineService,
                                      PadesInspector padesInspector) {
        this.documentoRepository = documentoRepository;
        this.versionRepository = versionRepository;
        this.firmanteRepository = firmanteRepository;
        this.chainVerifier = chainVerifier;
        this.applicationSignatureVerifier = applicationSignatureVerifier;
        this.integrityGuard = integrityGuard;
        this.timelineService = timelineService;
        this.padesInspector = padesInspector;
    }

    @Transactional
    public VerificacionPublicaResponse verificarPorCodigo(String codigoIngresado, String ip) {
        String codigo = normalizar(codigoIngresado);
        Optional<DocumentoFirma> encontrado = codigo.isEmpty() ? Optional.empty() : documentoRepository.findByCodigoVerificacion(codigo);
        if (encontrado.isEmpty()) {
            return VerificacionPublicaResponse.noRegistrado(
                    "El código consultado no corresponde a ningún documento emitido por SIE Jurídicos. "
                            + "Verifique que lo copió completo, tal como aparece impreso en el documento.", null, null);
        }
        DocumentoFirma documento = encontrado.get();
        Evaluacion evaluacion = evaluarRegistro(documento);
        registrarConsulta(documento, "código de verificación", evaluacion.alterado, ip);
        String detalle = evaluacion.alterado
                ? "El registro de este documento presenta señales de alteración (" + String.join("; ", evaluacion.hallazgos)
                        + "). No confíe en su contenido y comuníquese con SIE Jurídicos."
                : "El documento fue emitido por SIE Jurídicos y su registro de custodia (cadena de hashes de eventos y "
                        + "firma de aplicación) se encuentra íntegro. Para confirmar que una copia concreta no fue modificada, "
                        + "súbala en la verificación de archivo PDF (Ley 527 de 1999, art. 9: integridad del mensaje de datos).";
        return construir(documento, evaluacion, detalle, true, null, null);
    }

    @Transactional
    public VerificacionPublicaResponse verificarPdf(byte[] pdf, String ip) {
        String hash = HashUtil.sha256Hex(pdf);
        VerificacionPublicaResponse.FirmaPades pades = padesInspector.inspeccionar(pdf);

        Optional<DocumentoFirma> vigente = documentoRepository.findFirstByHashContenidoIgnoreCase(hash);
        if (vigente.isPresent()) {
            return veredictoPorCoincidencia(vigente.get(), true, hash, pades, ip);
        }
        Optional<DocumentoVersion> version = versionRepository.findFirstByHashContenidoIgnoreCase(hash);
        if (version.isPresent()) {
            return veredictoPorCoincidencia(version.get().getDocumento(), false, hash, pades, ip);
        }

        // Ninguna versión registrada tiene este hash: si el archivo dice provenir de SIE
        // (código en sus metadatos o firma con una llave PAdES de SIE), es una copia modificada.
        String codigoMetadatos = normalizar(extraerCodigoDeMetadatos(pdf));
        Optional<DocumentoFirma> origen = codigoMetadatos.isEmpty() ? Optional.empty()
                : documentoRepository.findByCodigoVerificacion(codigoMetadatos);
        if (origen.isEmpty() && !pades.emitidaPorSie()) {
            return VerificacionPublicaResponse.noRegistrado(
                    "El archivo no coincide con ninguna versión registrada por SIE Jurídicos y no presenta su firma "
                            + "digital ni su código de verificación. No es posible afirmar que lo haya emitido SIE.",
                    hash, pades);
        }
        List<String> hallazgos = new ArrayList<>();
        hallazgos.add("el hash SHA-256 del archivo no coincide con ninguna versión registrada del documento");
        agregarHallazgosPades(pades, hallazgos);
        String motivo = motivoAlteracion(pades);
        String detalle = "El archivo presenta señales de haber sido alterado después de su emisión por SIE Jurídicos: "
                + motivo + " Este archivo no es una copia fiel del documento original y no debe tenerse como tal "
                + "(Ley 527 de 1999, art. 9).";
        if (origen.isPresent()) {
            DocumentoFirma documento = origen.get();
            Evaluacion evaluacion = evaluarRegistro(documento);
            evaluacion.hallazgos.addAll(0, hallazgos);
            evaluacion.alterado = true;
            registrarConsulta(documento, "archivo PDF (hash " + hash.substring(0, 16) + "…)", true, ip);
            return construir(documento, evaluacion, detalle, false, hash, pades);
        }
        return new VerificacionPublicaResponse(VerificacionVeredicto.ALTERADO, detalle, null, null, null, null,
                null, false, hash, null, null, null, null, pades, hallazgos);
    }

    private VerificacionPublicaResponse veredictoPorCoincidencia(DocumentoFirma documento, boolean versionVigente, String hash,
                                                                 VerificacionPublicaResponse.FirmaPades pades, String ip) {
        Evaluacion evaluacion = evaluarRegistro(documento);
        if (pades.firmasEncontradas() > 0) {
            if (!pades.firmaValida() || !pades.cubreTodoElDocumento()) {
                evaluacion.alterado = true;
                agregarHallazgosPades(pades, evaluacion.hallazgos);
            }
        }
        registrarConsulta(documento, "archivo PDF (hash " + hash.substring(0, 16) + "…)", evaluacion.alterado, ip);
        String detalle;
        if (evaluacion.alterado) {
            detalle = "El archivo coincide con una versión registrada, pero el registro de custodia del documento presenta señales "
                    + "de alteración (" + String.join("; ", evaluacion.hallazgos) + "). Comuníquese con SIE Jurídicos.";
        } else if (versionVigente) {
            detalle = "El archivo es idéntico, byte a byte, al documento sellado por SIE Jurídicos (hash SHA-256 coincidente) "
                    + "y su registro de custodia está íntegro. No ha sido modificado desde su emisión (Ley 527 de 1999, art. 9).";
        } else {
            detalle = "El archivo es idéntico a una versión anterior (borrador) registrada por SIE Jurídicos, no ha sido "
                    + "alterado, pero esa versión fue reemplazada: la versión vigente del documento es otra.";
        }
        return construir(documento, evaluacion, detalle, versionVigente, hash, pades);
    }

    // Capas de evidencia del REGISTRO del documento (independientes del archivo subido).
    private Evaluacion evaluarRegistro(DocumentoFirma documento) {
        Evaluacion evaluacion = new Evaluacion();
        evaluacion.cadena = chainVerifier.verificar(documento);
        if (!evaluacion.cadena.integra()) {
            evaluacion.alterado = true;
            evaluacion.hallazgos.add("la cadena de hashes de eventos está rota en la secuencia " + evaluacion.cadena.secuenciaRota());
        }
        evaluacion.firmaAplicacionValida = applicationSignatureVerifier.verificar(documento);
        if (!evaluacion.firmaAplicacionValida) {
            evaluacion.alterado = true;
            evaluacion.hallazgos.add("la firma criptográfica de aplicación no coincide con los datos actuales del documento");
            integrityGuard.marcarAlterado(documento,
                    "La firma criptográfica de aplicación no coincide con los datos actuales del documento "
                            + "(detectado por el portal público de verificación).",
                    DocumentoEvento.ACTOR_SISTEMA, null);
        }
        if (DocumentoFirma.INTEGRIDAD_ALTERADO.equals(documento.getEstadoIntegridad())) {
            evaluacion.alterado = true;
            if (evaluacion.hallazgos.isEmpty()) {
                evaluacion.hallazgos.add("el documento fue marcado como ALTERADO en una verificación anterior");
            }
        }
        return evaluacion;
    }

    private void registrarConsulta(DocumentoFirma documento, String medio, boolean alterado, String ip) {
        timelineService.registrar(documento, DocumentoEvento.TIPO_VERIFICACION_CONSULTADA, DocumentoEvento.ACTOR_SISTEMA, null, null,
                "Verificación pública por " + medio + ". Veredicto: " + (alterado ? "ALTERADO" : "ÍNTEGRO") + ".", ip, null);
    }

    private VerificacionPublicaResponse construir(DocumentoFirma documento, Evaluacion evaluacion, String detalle,
                                                  boolean versionVigente, String hashConsultado,
                                                  VerificacionPublicaResponse.FirmaPades pades) {
        long anteriores = versionRepository.countByDocumentoId(documento.getId());
        List<VerificacionPublicaResponse.Firmante> firmantes = firmanteRepository
                .findByDocumentoIdOrderByOrdenAsc(documento.getId()).stream().map(this::aFirmante).toList();
        VerificacionPublicaResponse.SelloTiempo sello = new VerificacionPublicaResponse.SelloTiempo(
                documento.getRfc3161Token() != null, documento.getRfc3161UrlTsa(),
                documento.getRfc3161SelladoEn() != null ? documento.getRfc3161SelladoEn().format(FECHA_HORA) : null);
        return new VerificacionPublicaResponse(
                evaluacion.alterado ? VerificacionVeredicto.ALTERADO : VerificacionVeredicto.INTEGRO,
                detalle,
                documento.getNombre(),
                documento.getFechaCreacion() != null ? FechaEsCo.largo(documento.getFechaCreacion()) : null,
                documento.getEstadoDocumento(),
                documento.getEstadoIntegridad(),
                (int) anteriores + 1,
                versionVigente,
                hashConsultado != null ? hashConsultado : documento.getHashContenido(),
                firmantes,
                sello,
                new VerificacionPublicaResponse.CadenaEventos(evaluacion.cadena.integra(), evaluacion.cadena.secuenciaRota(),
                        evaluacion.cadena.detalle()),
                evaluacion.firmaAplicacionValida,
                pades,
                List.copyOf(evaluacion.hallazgos));
    }

    private VerificacionPublicaResponse.Firmante aFirmante(DocumentoFirmante f) {
        return new VerificacionPublicaResponse.Firmante(enmascararNombre(f.getNombreCapturado()), rol(f.getTipoFirmante()),
                f.getEstado(), f.getFirmadoEn() != null ? f.getFirmadoEn().format(FECHA_HORA) : null);
    }

    private static String rol(String tipoFirmante) {
        if (tipoFirmante == null) {
            return "Firmante";
        }
        return switch (tipoFirmante) {
            case DocumentoFirmante.TIPO_STAFF_GERENTE -> "Gerente de SIE Jurídicos";
            case DocumentoFirmante.TIPO_STAFF_ABOGADO -> "Abogado de SIE Jurídicos";
            case DocumentoFirmante.TIPO_PARTE -> "Parte firmante";
            default -> "Firmante";
        };
    }

    // "Mónica Fernanda Ruiz" -> "M*** F*** R***": suficiente para reconocerse, sin exponer el nombre.
    static String enmascararNombre(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return "Firmante";
        }
        StringBuilder sb = new StringBuilder();
        for (String palabra : nombre.trim().split("\\s+")) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(palabra.substring(0, palabra.offsetByCodePoints(0, 1))).append("***");
        }
        return sb.toString();
    }

    private static void agregarHallazgosPades(VerificacionPublicaResponse.FirmaPades pades, List<String> hallazgos) {
        if (pades.firmasEncontradas() == 0) {
            hallazgos.add("el archivo no conserva la firma digital PAdES de SIE");
            return;
        }
        if (!pades.firmaValida()) {
            hallazgos.add("la firma digital PAdES no verifica: el contenido cambió después de firmarse");
        }
        if (!pades.cubreTodoElDocumento()) {
            hallazgos.add("existen revisiones incrementales posteriores a la firma digital");
        }
    }

    private static String motivoAlteracion(VerificacionPublicaResponse.FirmaPades pades) {
        if (pades.firmasEncontradas() > 0 && !pades.firmaValida()) {
            return "su firma digital ya no verifica contra el contenido, es decir, el contenido fue modificado después de firmarse.";
        }
        if (pades.firmasEncontradas() > 0 && !pades.cubreTodoElDocumento()) {
            return "se añadieron revisiones incrementales después de la firma digital, que no están amparadas por ella.";
        }
        return "su huella SHA-256 no coincide con la de ninguna versión registrada.";
    }

    // Busca "verificacion:XXXX-..." en el campo Keywords de los metadatos del PDF; null si no hay.
    private static String extraerCodigoDeMetadatos(byte[] archivo) {
        try {
            PdfReader reader = new PdfReader(archivo);
            try {
                String palabrasClave = reader.getInfo().get("Keywords");
                if (palabrasClave == null) {
                    return null;
                }
                int inicio = palabrasClave.indexOf(MARCA_CODIGO);
                if (inicio < 0) {
                    return null;
                }
                String resto = palabrasClave.substring(inicio + MARCA_CODIGO.length());
                int coma = resto.indexOf(',');
                return (coma >= 0 ? resto.substring(0, coma) : resto).trim();
            } finally {
                reader.close();
            }
        } catch (Exception ex) {
            return null;
        }
    }

    private static String normalizar(String codigo) {
        return codigo == null ? "" : codigo.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-F0-9-]", "");
    }

    private static final class Evaluacion {
        ResultadoVerificacionCadena cadena;
        Boolean firmaAplicacionValida;
        boolean alterado;
        final List<String> hallazgos = new ArrayList<>();
    }
}
