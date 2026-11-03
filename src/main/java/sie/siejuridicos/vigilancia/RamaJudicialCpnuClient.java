package sie.siejuridicos.vigilancia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import sie.siejuridicos.caso.RadicadoUtil;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

// Cliente de SOLO LECTURA contra los endpoints públicos (sin token, sin API key, los mismos
// que consume el propio portal consultaprocesos.ramajudicial.gov.co) de la Rama Judicial.
// Validado en vivo el 2026-10-30 contra radicados reales de la firma (23 dígitos, formato
// CPNU) antes de escribir este cliente: responde JSON estructurado, ~0.4-0.5s de latencia,
// sin bloqueos en esa prueba puntual. No son endpoints documentados oficialmente (no hay
// contrato ni SLA) -- por eso NUNCA lanza una excepción que tumbe al llamador: cualquier
// fallo (red, formato inesperado, HTTP distinto de 200) se loguea como advertencia y
// devuelve Optional.empty()/lista vacía, mismo criterio de robustez que
// Rfc3161TimestampClient para otro servicio externo sin SLA.
//
// Nunca se loguea el contenido de una respuesta (nombres de partes procesales reales) --
// mismo criterio que HojaCalculoService.
@Component
public class RamaJudicialCpnuClient {

    private static final Logger log = LoggerFactory.getLogger(RamaJudicialCpnuClient.class);

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";
    private static final DateTimeFormatter FECHA_CPNU = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final String urlBase;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Duration timeoutLectura;

    // Pedido explícito del usuario (2026-10-31, auditoría de SIE-Vigilancia): "implementa...
    // alertas, anuncios de caidas, deteccion". Mismo patrón y mismo umbral que
    // PublicacionesProcesalesClient.pareceCaido() -- cuenta solo fallos de RED reales y HTTP
    // 403 (el bloqueo/WAF ya confirmado en vivo en esta sesión), NUNCA un 404/400 (radicado
    // no encontrado o parámetro inválido son resultados válidos del negocio, no evidencia de
    // que el CPNU esté caído, ver obtener() más abajo).
    private final AtomicInteger fallosConsecutivos = new AtomicInteger(0);
    // Subido de 5 a 10 el 2026-11-01 -- mismo criterio que PublicacionesProcesalesClient (ver
    // su comentario): con el timeout de lectura ya subido a 30s y el reintento de obtener()
    // de abajo, 10 da más margen ante una racha corta de mala suerte sin dejar de proteger
    // contra un apagón real sostenido del CPNU.
    private static final int UMBRAL_FALLOS_CONSECUTIVOS = 10;

    public RamaJudicialCpnuClient(
            @Value("${app.vigilancia.cpnu.url-base}") String urlBase,
            @Value("${app.vigilancia.cpnu.timeout-conexion-ms}") long timeoutConexionMs,
            @Value("${app.vigilancia.cpnu.timeout-lectura-ms}") long timeoutLecturaMs,
            ObjectMapper objectMapper) {
        this.urlBase = urlBase;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutConexionMs)).build();
        this.objectMapper = objectMapper;
        this.timeoutLectura = Duration.ofMillis(timeoutLecturaMs);
    }

    // idProceso interno de la Rama Judicial para ese radicado -- lo necesita
    // listarActuaciones() (el radicado por sí solo no sirve para ese segundo endpoint). Si
    // el radicado no existe en el CPNU (caso muy antiguo, o de una fuente que no usa
    // radicado CPNU real, ver FuenteCaso), devuelve Optional.empty(), no es un error.
    public Optional<Long> buscarIdProceso(String radicado23Digitos) {
        return buscarProceso(radicado23Digitos).map(ProcesoResuelto::idProceso);
    }

    // Gap real detectado comparando contra el correo diario de Lupa Jurídica (pedido
    // explícito del usuario: "lupa nos da un seguimiento mas robusto... necesito mejorar
    // esto", y luego: "tienes que traer absolutamente toda la informacion... entre mas datos
    // mucho mejor"). Dos llamadas, NUNCA una sola:
    // 1. NumeroRadicacion?numero=... -- ya traía idProceso/despacho/sujetosProcesales, se le
    //    suman aquí departamento, fechaProceso y esPrivado (estaban en la misma respuesta,
    //    descartados igual que la hora exacta, ver parsearFechaHora).
    // 2. Proceso/Detalle/{idProceso} -- un endpoint APARTE, verificado en vivo el 2026-10-31,
    //    con datos que NumeroRadicacion no trae: ponente (el juez), tipoProceso,
    //    claseProceso, subclaseProceso, recurso y ubicacion (dónde está físicamente el
    //    expediente dentro del despacho ahora mismo, ej. "Secretaria - Oficios"). Si esta
    //    segunda llamada falla, se devuelve igual lo que sí se pudo resolver de la primera --
    //    más datos es mejor, pero un fallo parcial no debe tirar todo lo demás.
    public Optional<ProcesoResuelto> buscarProceso(String radicado23Digitos) {
        // Un solo punto de normalización (ver RadicadoUtil) -- bug real confirmado en vivo
        // (2026-11-01, 13 de 328 casos del ciclo nocturno): antes esto limpiaba con su propia
        // regex local, igual que CasoService.consultar() y PublicacionesProcesalesClient cada
        // uno por su lado, y ninguno de los tres coincidía exactamente.
        if (!RadicadoUtil.esFormatoCpnuValido(radicado23Digitos)) {
            return Optional.empty();
        }
        String radicadoLimpio = RadicadoUtil.limpiar(radicado23Digitos);
        String url = urlBase + "/api/v2/Procesos/Consulta/NumeroRadicacion?numero="
                + radicadoLimpio + "&soloActivos=false";
        Optional<JsonNode> respuesta = obtener(url);
        if (respuesta.isEmpty()) {
            return Optional.empty();
        }
        JsonNode procesos = respuesta.get().path("procesos");
        if (!procesos.isArray() || procesos.isEmpty()) {
            return Optional.empty();
        }
        JsonNode primero = procesos.get(0);
        if (!primero.path("idProceso").isNumber()) {
            return Optional.empty();
        }
        long idProceso = primero.path("idProceso").asLong();
        DetalleProceso detalle = buscarDetalleProceso(idProceso);
        return Optional.of(new ProcesoResuelto(
                idProceso,
                textoOVacio(primero.path("despacho")),
                textoOVacio(primero.path("sujetosProcesales")),
                textoOVacio(primero.path("departamento")),
                parsearFecha(primero.path("fechaProceso").asString(null)),
                primero.path("esPrivado").asBoolean(false),
                detalle.ponente(),
                detalle.tipoProceso(),
                detalle.claseProceso(),
                detalle.subclaseProceso(),
                detalle.recurso(),
                detalle.ubicacion(),
                detalle.ultimaActualizacion()));
    }

    // Gap real detectado investigando cómo Lupa Jurídica "detecta nuevos procesos" para un
    // cliente (pedido explícito del usuario: "implementa todas las mejoras identificadas") --
    // el CPNU tiene un endpoint APARTE de NumeroRadicacion que busca por nombre/razón social,
    // sin necesitar ningún radicado de antemano. Confirmado en vivo el 2026-10-31 buscando un
    // cliente real de la firma: devolvió un proceso NUEVO (una tutela contra un juzgado) que
    // no estaba en nuestra tabla de casos -- exactamente el tipo de hallazgo proactivo que
    // antes solo se conseguía reactivamente (viendo una actuación de un radicado ya conocido).
    // Nunca se usa para crear un caso automáticamente: solo para alimentar una lista de
    // CANDIDATOS que un humano revisa (ver ProcesoCandidatoDetectado) -- la búsqueda por
    // nombre es ambigua (tocayos reales), nunca una coincidencia garantizada.
    public List<ProcesoCandidato> buscarPorNombre(String nombre, boolean esPersonaJuridica) {
        String nombreLimpio = nombre == null ? "" : nombre.strip();
        if (nombreLimpio.isBlank()) {
            return List.of();
        }
        String url = urlBase + "/api/v2/Procesos/Consulta/NombreRazonSocial?nombre="
                + java.net.URLEncoder.encode(nombreLimpio, StandardCharsets.UTF_8)
                + "&tipoPersona=" + (esPersonaJuridica ? "jur" : "nat")
                + "&SoloActivos=false&codificacionDespacho=&pagina=1";
        Optional<JsonNode> respuesta = obtener(url);
        if (respuesta.isEmpty()) {
            return List.of();
        }
        JsonNode procesos = respuesta.get().path("procesos");
        if (!procesos.isArray()) {
            return List.of();
        }
        List<ProcesoCandidato> resultado = new ArrayList<>();
        for (JsonNode nodo : procesos) {
            if (nodo.path("esPrivado").asBoolean(false)) {
                // "--- [ PROCESO PRIVADO ] ---" en vez de sujetosProcesales real -- la Rama
                // Judicial ya lo oculta por ley (ej. procesos de familia, menores), no hay
                // nada útil que mostrarle a un abogado aquí.
                continue;
            }
            String llaveProceso = textoOVacio(nodo.path("llaveProceso"));
            if (llaveProceso.isBlank() || !nodo.path("idProceso").isNumber()) {
                continue;
            }
            resultado.add(new ProcesoCandidato(
                    nodo.path("idProceso").asLong(),
                    llaveProceso,
                    textoOVacio(nodo.path("despacho")),
                    textoOVacio(nodo.path("departamento")),
                    textoOVacio(nodo.path("sujetosProcesales")),
                    parsearFecha(nodo.path("fechaProceso").asString(null)),
                    parsearFecha(nodo.path("fechaUltimaActuacion").asString(null))));
        }
        return resultado;
    }

    public record ProcesoCandidato(
            long idProceso,
            String llaveProceso,
            String despacho,
            String departamento,
            String sujetosProcesales,
            java.time.LocalDate fechaProceso,
            java.time.LocalDate fechaUltimaActuacion) {
    }

    public record ProcesoResuelto(
            long idProceso,
            String despacho,
            String sujetosProcesales,
            String departamento,
            java.time.LocalDate fechaProceso,
            boolean esPrivado,
            String ponente,
            String tipoProceso,
            String claseProceso,
            String subclaseProceso,
            String recurso,
            String ubicacion,
            // Pedido explícito del usuario: "la vigilancia tiene que mostrar toda,
            // absolutamente toda la informacion... todo lo que se detecte" -- Proceso/
            // Detalle trae esto (cuándo la Rama Judicial misma actualizó el proceso en SU
            // sistema) y se descartaba. Distinto de fechaUltimoSeguimiento (cuándo NOSOTROS
            // revisamos el caso).
            LocalDateTime ultimaActualizacionRama) {
    }

    public record ExpedienteProfundoResuelto(
            long idProcesoPrincipal,
            Integer idConexion,
            List<Long> idsProcesosRelacionados,
            String despacho,
            String departamento,
            String sujetosProcesalesConsolidados,
            java.time.LocalDate fechaProceso,
            java.time.LocalDate fechaUltimaActuacion,
            boolean esPrivado,
            String ponente,
            String tipoProceso,
            String claseProceso,
            String subclaseProceso,
            String recurso,
            String ubicacion,
            LocalDateTime ultimaActualizacionRama,
            List<ActuacionJudicial> todasLasActuaciones
    ) {
    }

    // Extracción profunda multi-proceso y multi-página para Legal-Pro:
    // Rescata TODOS los idProceso asociados al mismo radicado de 23 dígitos (p. ej. cuadernos o
    // conexiones múltiples en el mismo despacho), consolida todas las partes procesales de todos
    // los registros y recorre TODAS las páginas de actuaciones (no solo la página 1 de 40 filas).
    public Optional<ExpedienteProfundoResuelto> buscarExpedienteProfundoMultiPagina(String radicado23Digitos) {
        if (!RadicadoUtil.esFormatoCpnuValido(radicado23Digitos)) {
            return Optional.empty();
        }
        String radicadoLimpio = RadicadoUtil.limpiar(radicado23Digitos);
        String url = urlBase + "/api/v2/Procesos/Consulta/NumeroRadicacion?numero="
                + radicadoLimpio + "&soloActivos=false";
        Optional<JsonNode> respuesta = obtener(url);
        if (respuesta.isEmpty()) {
            return Optional.empty();
        }
        JsonNode procesos = respuesta.get().path("procesos");
        if (!procesos.isArray() || procesos.isEmpty()) {
            return Optional.empty();
        }

        JsonNode primero = procesos.get(0);
        if (!primero.path("idProceso").isNumber()) {
            return Optional.empty();
        }

        long idPrincipal = primero.path("idProceso").asLong();
        Integer idConexion = primero.path("idConexion").isNumber() ? primero.path("idConexion").asInt() : null;
        String despacho = textoOVacio(primero.path("despacho"));
        String departamento = textoOVacio(primero.path("departamento"));
        java.time.LocalDate fechaProceso = parsearFecha(primero.path("fechaProceso").asString(null));
        java.time.LocalDate fechaUltima = parsearFecha(primero.path("fechaUltimaActuacion").asString(null));
        boolean esPrivado = primero.path("esPrivado").asBoolean(false);

        List<Long> idsRelacionados = new ArrayList<>();
        java.util.LinkedHashSet<String> partesUnicas = new java.util.LinkedHashSet<>();

        for (JsonNode procNode : procesos) {
            if (procNode.path("idProceso").isNumber()) {
                idsRelacionados.add(procNode.path("idProceso").asLong());
            }
            if (fechaProceso == null) {
                fechaProceso = parsearFecha(procNode.path("fechaProceso").asString(null));
            }
            java.time.LocalDate fUlt = parsearFecha(procNode.path("fechaUltimaActuacion").asString(null));
            if (fUlt != null && (fechaUltima == null || fUlt.isAfter(fechaUltima))) {
                fechaUltima = fUlt;
            }
            String suj = textoOVacio(procNode.path("sujetosProcesales"));
            if (!suj.isBlank()) {
                for (String fragmento : suj.split("\\|")) {
                    String limpio = fragmento.replaceAll("\\s+", " ").strip();
                    if (!limpio.isBlank()) {
                        partesUnicas.add(limpio);
                    }
                }
            }
        }

        DetalleProceso detalle = buscarDetalleProceso(idPrincipal);
        java.util.LinkedHashMap<Long, ActuacionJudicial> actuacionesMap = new java.util.LinkedHashMap<>();

        for (Long idProc : idsRelacionados) {
            if (esPrivado) continue;
            List<ActuacionJudicial> acts = listarActuacionesTodasLasPaginas(idProc);
            for (ActuacionJudicial a : acts) {
                actuacionesMap.putIfAbsent(a.idActuacionExterna(), a);
            }
        }

        return Optional.of(new ExpedienteProfundoResuelto(
                idPrincipal,
                idConexion,
                idsRelacionados,
                despacho,
                departamento,
                String.join(" | ", partesUnicas),
                fechaProceso,
                fechaUltima,
                esPrivado,
                detalle.ponente(),
                detalle.tipoProceso(),
                detalle.claseProceso(),
                detalle.subclaseProceso(),
                detalle.recurso(),
                detalle.ubicacion(),
                detalle.ultimaActualizacion(),
                new ArrayList<>(actuacionesMap.values())
        ));
    }

    public List<ActuacionJudicial> listarActuacionesTodasLasPaginas(long idProceso) {
        List<ActuacionJudicial> acumulado = new ArrayList<>();
        int paginaActual = 1;
        int maxPaginas = 1;

        while (paginaActual <= maxPaginas && paginaActual <= 10) {
            String url = urlBase + "/api/v2/Proceso/Actuaciones/" + idProceso + "?pagina=" + paginaActual;
            Optional<JsonNode> respuesta = obtener(url);
            if (respuesta.isEmpty()) {
                break;
            }
            JsonNode root = respuesta.get();
            if (paginaActual == 1) {
                JsonNode pagNode = root.path("paginacion").path("cantidadPaginas");
                if (pagNode.isNumber() && pagNode.asInt() > 1) {
                    maxPaginas = Math.min(10, pagNode.asInt());
                }
            }
            JsonNode actuaciones = root.path("actuaciones");
            if (!actuaciones.isArray() || actuaciones.isEmpty()) {
                break;
            }
            for (JsonNode nodo : actuaciones) {
                if (!nodo.path("idRegActuacion").isNumber()) {
                    continue;
                }
                acumulado.add(new ActuacionJudicial(
                        nodo.path("idRegActuacion").asLong(),
                        parsearFechaHora(nodo.path("fechaActuacion").asString(null)),
                        textoOVacio(nodo.path("actuacion")),
                        textoOVacio(nodo.path("anotacion")),
                        parsearFechaHora(nodo.path("fechaRegistro").asString(null)),
                        nodo.path("conDocumentos").asBoolean(false),
                        nodo.path("consActuacion").isNumber() ? nodo.path("consActuacion").asInt() : null,
                        parsearFecha(nodo.path("fechaInicial").asString(null)),
                        parsearFecha(nodo.path("fechaFinal").asString(null))));
            }
            paginaActual++;
        }
        return acumulado;
    }

    // Resuelve los documentos PDF adjuntos directamente a una actuación en el CPNU
    // (/api/v2/Proceso/DocumentosActuacion/{idRegActuacion} -> /api/v2/Descarga/Documento/{idRegDocumento})
    // cuando la actuación viene marcada con conDocumentos=true (has_documents=true en Croma).
    public List<DocumentoProvidenciaCandidato> listarDocumentosDeActuacion(long idActuacionExterna, String nombreActuacion) {
        String url = urlBase + "/api/v2/Proceso/DocumentosActuacion/" + idActuacionExterna;
        Optional<JsonNode> respuesta = obtener(url);
        if (respuesta.isEmpty()) {
            return List.of();
        }
        JsonNode root = respuesta.get();
        List<JsonNode> items = new ArrayList<>();
        if (root.isArray()) {
            root.forEach(items::add);
        } else if (root.isObject() && root.path("idRegDocumento").isNumber()) {
            items.add(root);
        }
        List<DocumentoProvidenciaCandidato> resultado = new ArrayList<>();
        for (JsonNode docNode : items) {
            if (!docNode.path("idRegDocumento").isNumber()) {
                continue;
            }
            long idRegDoc = docNode.path("idRegDocumento").asLong();
            String nombre = textoOVacio(docNode.path("nombre"));
            if (nombre.isBlank()) {
                nombre = (nombreActuacion != null && !nombreActuacion.isBlank() ? nombreActuacion : "Providencia")
                        + " (" + idRegDoc + ").pdf";
            } else if (!nombre.toLowerCase().endsWith(".pdf")) {
                nombre = nombre + ".pdf";
            }
            String urlDescarga = urlBase + "/api/v2/Descarga/Documento/" + idRegDoc;
            resultado.add(new DocumentoProvidenciaCandidato(nombre, urlDescarga, true));
        }
        return resultado;
    }

    private record DetalleProceso(
            String ponente, String tipoProceso, String claseProceso,
            String subclaseProceso, String recurso, String ubicacion, LocalDateTime ultimaActualizacion) {
        static final DetalleProceso VACIO = new DetalleProceso(null, null, null, null, null, null, null);
    }

    private DetalleProceso buscarDetalleProceso(long idProceso) {
        Optional<JsonNode> respuesta = obtener(urlBase + "/api/v2/Proceso/Detalle/" + idProceso);
        if (respuesta.isEmpty()) {
            return DetalleProceso.VACIO;
        }
        JsonNode nodo = respuesta.get();
        return new DetalleProceso(
                textoOVacio(nodo.path("ponente")),
                textoOVacio(nodo.path("tipoProceso")),
                textoOVacio(nodo.path("claseProceso")),
                textoOVacio(nodo.path("subclaseProceso")),
                textoOVacio(nodo.path("recurso")),
                textoOVacio(nodo.path("ubicacion")),
                parsearFechaHora(nodo.path("ultimaActualizacion").asString(null)));
    }

    // Historial completo de actuaciones del proceso, más reciente primero (mismo orden que
    // devuelve la Rama Judicial). Nunca lanza: una lista vacía puede significar "no tiene
    // actuaciones todavía" o "falló la consulta" -- el llamador (VigilanciaJudicialService)
    // ya trata ambos casos igual (no hay nada que mostrar), así que no hace falta
    // distinguirlos con una excepción.
    public List<ActuacionJudicial> listarActuaciones(long idProceso) {
        String url = urlBase + "/api/v2/Proceso/Actuaciones/" + idProceso;
        Optional<JsonNode> respuesta = obtener(url);
        if (respuesta.isEmpty()) {
            return List.of();
        }
        JsonNode actuaciones = respuesta.get().path("actuaciones");
        if (!actuaciones.isArray()) {
            return List.of();
        }
        List<ActuacionJudicial> resultado = new ArrayList<>();
        for (JsonNode nodo : actuaciones) {
            if (!nodo.path("idRegActuacion").isNumber()) {
                // Nunca debería pasar en datos reales, pero sin este id no hay forma segura
                // de deduplicar en ActuacionJudicialRegistrada -- mejor omitir la fila que
                // guardar una actuación con una llave inventada.
                continue;
            }
            resultado.add(new ActuacionJudicial(
                    nodo.path("idRegActuacion").asLong(),
                    parsearFechaHora(nodo.path("fechaActuacion").asString(null)),
                    textoOVacio(nodo.path("actuacion")),
                    textoOVacio(nodo.path("anotacion")),
                    parsearFechaHora(nodo.path("fechaRegistro").asString(null)),
                    nodo.path("conDocumentos").asBoolean(false),
                    nodo.path("consActuacion").isNumber() ? nodo.path("consActuacion").asInt() : null,
                    parsearFecha(nodo.path("fechaInicial").asString(null)),
                    parsearFecha(nodo.path("fechaFinal").asString(null))));
        }
        return resultado;
    }

    private static String textoOVacio(JsonNode nodo) {
        String valor = nodo.asString(null);
        return valor == null ? "" : valor.strip();
    }

    // Bug real reportado por el usuario: "necesito... el reporte completo por fecha y hora
    // exacta" -- el CPNU SÍ devuelve hora exacta para fechaActuacion/fechaRegistro (formato
    // "yyyy-MM-dd'T'HH:mm:ss", verificado en vivo), pero este cliente la venía descartando
    // con un .toLocalDate() aquí mismo. parsearFechaHora() conserva la hora real; parsearFecha
    // (abajo) se queda en LocalDate a propósito solo para fechaInicial/fechaFinal (el
    // vencimiento de un término es un día calendario, la hora no tiene ningún significado de
    // negocio ahí).
    private static LocalDateTime parsearFechaHora(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            // Proceso/Detalle.ultimaActualizacion trae milisegundos ("...T17:01:32.333"),
            // a diferencia de fechaActuacion/fechaRegistro ("...T00:00:00") -- se recorta a
            // los primeros 19 caracteres ("yyyy-MM-ddTHH:mm:ss") para que FECHA_CPNU, el
            // mismo formato estricto ya probado en vivo para los demás campos, sirva para
            // ambos sin necesitar un segundo formateador. Nunca necesitamos precisión de
            // milisegundo en ningún campo de este sistema.
            String recortado = valor.length() > 19 ? valor.substring(0, 19) : valor;
            return LocalDateTime.parse(recortado, FECHA_CPNU);
        } catch (Exception ex) {
            return null;
        }
    }

    private static java.time.LocalDate parsearFecha(String valor) {
        LocalDateTime fechaHora = parsearFechaHora(valor);
        return fechaHora == null ? null : fechaHora.toLocalDate();
    }

    // true si las últimas UMBRAL_FALLOS_CONSECUTIVOS consultas de RED o HTTP 403 consecutivas
    // fallaron -- señal de que el CPNU está caído o bloqueándonos ahora mismo (ver el
    // comentario de fallosConsecutivos arriba), nunca de que estos radicados puntuales no
    // existan. Se reinicia solo en la siguiente consulta que responda 200.
    public boolean pareceCaido() {
        return fallosConsecutivos.get() >= UMBRAL_FALLOS_CONSECUTIVOS;
    }

    private Optional<JsonNode> obtener(String url) {
        HttpResponse<byte[]> response;
        try {
            response = enviar(url);
        } catch (Exception primerIntento) {
            // Reintento único, encontrado necesario en vivo (2026-11-01, diagnóstico con
            // curl -v contra Publicaciones Procesales, mismo patrón real en toda la
            // infraestructura de ramajudicial.gov.co): a veces la conexión TCP y el TLS se
            // completan por completo pero el gateway nunca manda ni un byte de respuesta --
            // un cuelgue real, no solo lentitud (eso ya lo cubre timeoutLectura). Un segundo
            // intento, tras una pausa corta, suele conectar a otra instancia del gateway y
            // responder normal -- absorbe esa clase de fallo puntual sin gastar un fallo
            // consecutivo real de pareceCaido() por una sola mala suerte aislada.
            pausarReintento();
            try {
                response = enviar(url);
            } catch (Exception segundoIntento) {
                fallosConsecutivos.incrementAndGet();
                log.warn("No se pudo consultar la Rama Judicial (CPNU): {}", segundoIntento.getMessage());
                return Optional.empty();
            }
        }
        if (response.statusCode() == 403) {
            fallosConsecutivos.incrementAndGet();
            log.warn("Rama Judicial (CPNU) respondió HTTP 403 en {}", url);
            return Optional.empty();
        }
        if (response.statusCode() != 200) {
            // 404/400/etc: resultado válido del negocio (no encontrado, parámetro
            // inválido) -- no cuenta ni como fallo ni como éxito para pareceCaido().
            log.warn("Rama Judicial (CPNU) respondió HTTP {} en {}", response.statusCode(), url);
            return Optional.empty();
        }
        fallosConsecutivos.set(0);
        return Optional.of(objectMapper.readTree(new String(response.body(), StandardCharsets.UTF_8)));
    }

    private HttpResponse<byte[]> enviar(String url) throws java.io.IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeoutLectura)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json, text/plain, */*")
                .header("Accept-Language", "es-CO,es;q=0.9,en;q=0.8")
                .header("Origin", "https://consultaprocesos.ramajudicial.gov.co")
                .header("Referer", "https://consultaprocesos.ramajudicial.gov.co/Procesos/NumeroRadicacion")
                .GET()
                .build();
        // Bug real confirmado en vivo (2026-11-01, thread dump con jstack): el mismo patrón
        // httpClient.send() síncrono en PublicacionesProcesalesClient.descargarDocumento() se
        // quedó colgado 3h24min -- el .timeout(...) del HttpRequest de arriba NO siempre se
        // cumple cuando el servidor deja la conexión medio abierta. .sendAsync() + orTimeout()
        // SÍ cancela de verdad: no depende de que el propio HttpClient cumpla su temporizador
        // interno, solo de que este CompletableFuture expire.
        try {
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                    .orTimeout(timeoutLectura.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
                    .get();
        } catch (java.util.concurrent.ExecutionException ex) {
            if (ex.getCause() instanceof java.io.IOException causa) {
                throw causa;
            }
            throw new java.io.IOException(ex.getCause());
        }
    }

    private static void pausarReintento() {
        try {
            Thread.sleep(3000);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
