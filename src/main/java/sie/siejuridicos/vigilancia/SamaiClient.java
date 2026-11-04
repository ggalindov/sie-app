package sie.siejuridicos.vigilancia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import sie.siejuridicos.caso.RadicadoUtil;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Cliente de SOLO LECTURA contra SAMAI (samai.consejodeestado.gov.co), el sistema público de
// consulta de procesos del Consejo de Estado y los Tribunales Administrativos -- la
// jurisdicción contencioso-administrativa, que RamaJudicialCpnuClient NUNCA cubre (el CPNU es
// solo Rama Judicial ordinaria). Gap real detectado comparando contra Lupa Jurídica (pedido
// explícito del usuario: "implementa esto... SAMAI").
//
// Validado en vivo el 2026-10-31 contra un radicado real del Consejo de Estado
// (11001032500020210012300, un recurso extraordinario de revisión) antes de escribir este
// cliente: responde JSON estructurado, sin captcha, sin autenticación real (el widget trae
// `data-authenticated="false"`) -- solo necesita la cookie de sesión/antiforgery que
// cualquier GET inicial a la página del buscador ya entrega. El aviso del propio sitio dice
// que los datos son "exclusivamente para fines de consulta" y no autoriza su "tratamiento,
// recolección o uso para análisis" -- mismo texto, mismo nivel de riesgo que el CPNU, que
// este proyecto ya consume así desde antes (ver memoria del proyecto).
//
// No son endpoints documentados oficialmente (no hay contrato ni SLA) -- por eso NUNCA lanza
// una excepción que tumbe al llamador: cualquier fallo se loguea como advertencia y devuelve
// Optional.empty(), mismo criterio que RamaJudicialCpnuClient.
//
// Nunca se loguea el contenido de una respuesta (nombres de partes procesales reales) --
// mismo criterio que RamaJudicialCpnuClient/HojaCalculoService.
@Component
public class SamaiClient {

    private static final Logger log = LoggerFactory.getLogger(SamaiClient.class);

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";
    private static final DateTimeFormatter FECHA_SAMAI = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    // "guid=XXXX" al final de urlDetalle (ej. "/Vistas/Casos/list_procesos.aspx?guid=110010325000202100123001100103").
    private static final Pattern GUID_EN_URL = Pattern.compile("guid=([0-9A-Za-z]+)");

    private final String urlBase;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Duration timeoutLectura;

    // Pedido explícito del usuario (2026-10-31, auditoría de SIE-Vigilancia): "implementa...
    // alertas, anuncios de caidas, deteccion". Mismo patrón que RamaJudicialCpnuClient/
    // PublicacionesProcesalesClient -- compartido entre establecerSesion() y enviarJson()
    // porque ambos pegan al mismo host SAMAI; cuenta solo fallos de RED reales y HTTP 403
    // (ya visto en vivo esta sesión: "SAMAI respondió HTTP 403"), nunca otro código de
    // negocio (proceso no encontrado, petición inválida).
    private final AtomicInteger fallosConsecutivos = new AtomicInteger(0);
    private final java.util.concurrent.atomic.AtomicReference<String> cookieSesion = new java.util.concurrent.atomic.AtomicReference<>("");
    private static final int UMBRAL_FALLOS_CONSECUTIVOS = 10;

    public SamaiClient(
            @Value("${app.vigilancia.samai.url-base}") String urlBase,
            @Value("${app.vigilancia.samai.timeout-conexion-ms}") long timeoutConexionMs,
            @Value("${app.vigilancia.samai.timeout-lectura-ms}") long timeoutLecturaMs,
            ObjectMapper objectMapper) {
        this.urlBase = urlBase;
        // CookieManager propio (no cosmético): el endpoint de búsqueda exige la cookie
        // __AntiXsrfToken que SOLO entrega un GET previo a procesos.aspx -- sin un
        // CookieHandler compartido entre esas dos llamadas, el POST llegaría sin ella y
        // SAMAI lo rechazaría. ACCEPT_ALL porque es el mismo host en ambas llamadas, nunca
        // se reutiliza este HttpClient para otro dominio.
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutConexionMs))
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .build();
        this.objectMapper = objectMapper;
        this.timeoutLectura = Duration.ofMillis(timeoutLecturaMs);
    }

    // Busca un proceso por su radicado real de 23 dígitos en el Consejo de Estado o un
    // Tribunal Administrativo. "corporacion" (el despacho exacto a buscar, exigido por SAMAI
    // cuando ambito=corporacion) se deriva de los primeros 7 dígitos del radicado mismo
    // (departamento + ciudad + tipo de entidad, ej. "1100103" para Bogotá/Consejo de Estado)
    // -- confirmado en vivo que esto SÍ coincide con el código real de corporación, sin
    // necesitar consultar antes el catálogo de 76 corporaciones que expone SAMAI aparte.
    public Optional<ProcesoResuelto> buscarProceso(String radicado23Digitos) {
        // Un solo punto de normalización (ver RadicadoUtil) -- mismo bug real que
        // RamaJudicialCpnuClient/PublicacionesProcesalesClient tenían (2026-11-01).
        if (!RadicadoUtil.esFormatoCpnuValido(radicado23Digitos)) {
            return Optional.empty();
        }
        String radicadoLimpio = RadicadoUtil.limpiar(radicado23Digitos);
        String corporacion = radicadoLimpio.substring(0, 7);

        if (!establecerSesion()) {
            return Optional.empty();
        }

        String cuerpoPeticion = """
                {"tipoBusqueda":"radicado","criterio":"%s","fraseExacta":false,"ambito":"corporacion",\
                "corporacion":"%s","seccion":"","ponente":"","fechaDesde":"","fechaHasta":"",\
                "estado":"","tipoParte":"","pagina":1,"tamanoPagina":10}\
                """.formatted(radicadoLimpio, corporacion);

        Optional<JsonNode> respuesta = enviarJson(urlBase + "/Vistas/Casos/Jprocesos.ashx/buscar", cuerpoPeticion);
        if (respuesta.isEmpty()) {
            return Optional.empty();
        }
        JsonNode data = respuesta.get();
        if (!data.path("ok").asBoolean(false)) {
            return Optional.empty();
        }
        JsonNode items = data.path("data").path("items");
        if (!items.isArray() || items.isEmpty()) {
            return Optional.empty();
        }
        JsonNode primero = items.get(0);
        if (primero.path("esPrivado").asBoolean(false)) {
            // Mismo criterio que RamaJudicialCpnuClient.buscarPorNombre(): un proceso
            // privado no trae nada útil que mostrarle a un abogado.
            return Optional.empty();
        }

        String guid = extraerGuid(textoOVacio(primero.path("urlDetalle")));
        if (guid.isBlank()) {
            return Optional.empty();
        }

        String demandante = textoOVacio(primero.path("demandante"));
        String demandado = textoOVacio(primero.path("demandado"));
        String sujetosProcesales = (demandante.isBlank() ? "" : "Demandante: " + demandante)
                + (demandante.isBlank() || demandado.isBlank() ? "" : " | ")
                + (demandado.isBlank() ? "" : "Demandado: " + demandado);

        return Optional.of(new ProcesoResuelto(
                guid,
                textoOVacio(primero.path("despacho")),
                sujetosProcesales,
                textoOVacio(primero.path("clase")),
                parsearFecha(primero.path("fechaIngreso").asString(null)),
                primero.path("esPrivado").asBoolean(false),
                textoOVacio(primero.path("ponente"))));
    }

    public record ProcesoResuelto(
            String guid,
            String despacho,
            String sujetosProcesales,
            String tipoProceso,
            LocalDate fechaProceso,
            boolean esPrivado,
            String ponente) {
    }

    public record ProcesoSamaiDto(
            String radicado,
            String despacho,
            String demandante,
            String demandado,
            String clase,
            LocalDate fechaIngreso,
            String ponente,
            String guid,
            boolean esPrivado
    ) {
        public String obtenerPartesTexto() {
            String dte = demandante == null ? "" : demandante.strip();
            String ddo = demandado == null ? "" : demandado.strip();
            if (dte.isBlank() && ddo.isBlank()) return "";
            if (dte.isBlank()) return "Ddo: " + ddo;
            if (ddo.isBlank()) return "Dte: " + dte;
            return "Dte: " + dte + " | Ddo: " + ddo;
        }
    }

    // Búsqueda multi-corporación en la jurisdicción contencioso-administrativa por Nombre, Cédula o NIT de parte procesal.
    // Consulta Consejo de Estado (1100103) y Tribunal Administrativo de Cundinamarca (2500023) por defecto.
    public List<ProcesoSamaiDto> buscarPorParte(String criterio, String corporacionOpcional) {
        if (criterio == null || criterio.strip().length() < 3) {
            return List.of();
        }
        if (!establecerSesion()) {
            return List.of();
        }

        List<String> corporaciones = (corporacionOpcional != null && !corporacionOpcional.isBlank())
                ? List.of(corporacionOpcional.strip())
                : List.of("1100103", "2500023");

        List<ProcesoSamaiDto> resultados = new ArrayList<>();
        String criterioLimpio = criterio.strip();

        for (String corp : corporaciones) {
            try {
                String cuerpoPeticion = """
                        {"tipoBusqueda":"parte","criterio":"%s","fraseExacta":false,"ambito":"corporacion",\
                        "corporacion":"%s","seccion":"","ponente":"","fechaDesde":"","fechaHasta":"",\
                        "estado":"","tipoParte":"","pagina":1,"tamanoPagina":15}\
                        """.formatted(criterioLimpio.replace("\"", "\\\""), corp);

                Optional<JsonNode> respuesta = enviarJson(urlBase + "/Vistas/Casos/Jprocesos.ashx/buscar", cuerpoPeticion);
                if (respuesta.isEmpty()) continue;
                JsonNode data = respuesta.get().path("data");
                JsonNode items = data.path("items");
                if (!items.isArray() || items.isEmpty()) continue;

                for (JsonNode item : items) {
                    if (item.path("esPrivado").asBoolean(false)) {
                        continue;
                    }
                    String rad = textoOVacio(item.path("radicado"));
                    if (rad.isBlank()) {
                        rad = textoOVacio(item.path("numeroProceso"));
                    }
                    String despacho = textoOVacio(item.path("nombreDespachoOperativo"));
                    if (despacho.isBlank()) {
                        despacho = textoOVacio(item.path("despacho"));
                    }
                    String dte = textoOVacio(item.path("demandante"));
                    String ddo = textoOVacio(item.path("demandado"));
                    String clase = textoOVacio(item.path("clase"));
                    LocalDate fechaIngreso = parsearFecha(item.path("fechaIngreso").asString(null));
                    String ponente = textoOVacio(item.path("ponente"));
                    String guid = extraerGuid(textoOVacio(item.path("urlDetalle")));
                    boolean esPrivado = item.path("esPrivado").asBoolean(false);

                    resultados.add(new ProcesoSamaiDto(
                            rad, despacho, dte, ddo, clase, fechaIngreso, ponente, guid, esPrivado));
                }
            } catch (Exception ex) {
                log.warn("Error consultando corporación {} en SAMAI para '{}': {}", corp, criterio, ex.getMessage());
            }
        }
        return resultados;
    }

    private static String extraerGuid(String urlDetalle) {
        Matcher m = GUID_EN_URL.matcher(urlDetalle);
        return m.find() ? m.group(1) : "";
    }

    // GET a la página del buscador para que SAMAI entregue la cookie __AntiXsrfToken que
    // exige el POST de búsqueda -- no hace falta leer el cuerpo, solo que la respuesta sea
    // 200 y que el CookieManager del HttpClient haya quedado con la cookie puesta.
    private boolean establecerSesion() {
        HttpResponse<Void> response;
        try {
            response = enviarGetSesion();
        } catch (Exception primerIntento) {
            pausarReintento();
            try {
                response = enviarGetSesion();
            } catch (Exception segundoIntento) {
                fallosConsecutivos.incrementAndGet();
                log.warn("No se pudo establecer sesión con SAMAI: {}", segundoIntento.getMessage());
                return false;
            }
        }
        if (response.statusCode() == 200) {
            fallosConsecutivos.set(0);
            List<String> setCookies = response.headers().allValues("set-cookie");
            if (!setCookies.isEmpty()) {
                List<String> pares = new ArrayList<>();
                for (String sc : setCookies) {
                    int p = sc.indexOf(';');
                    String cookiePar = (p != -1 ? sc.substring(0, p) : sc).trim();
                    if (!cookiePar.isBlank()) {
                        pares.add(cookiePar);
                    }
                }
                cookieSesion.set(String.join("; ", pares));
            }
            return true;
        }
        fallosConsecutivos.incrementAndGet();
        return false;
    }

    private HttpResponse<Void> enviarGetSesion() throws java.io.IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(urlBase + "/Vistas/Casos/procesos.aspx"))
                .timeout(timeoutLectura)
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();
        // Bug real confirmado en vivo (2026-11-01, thread dump con jstack): el mismo patrón
        // httpClient.send() síncrono en PublicacionesProcesalesClient.descargarDocumento() se
        // quedó colgado 3h24min -- el .timeout(...) del HttpRequest de arriba NO siempre se
        // cumple cuando el servidor deja la conexión medio abierta. .sendAsync() + orTimeout()
        // SÍ cancela de verdad.
        try {
            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .orTimeout(timeoutLectura.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
                    .get();
        } catch (java.util.concurrent.ExecutionException ex) {
            if (ex.getCause() instanceof java.io.IOException causa) {
                throw causa;
            }
            throw new java.io.IOException(ex.getCause());
        }
    }

    // Reintento único -- mismo hallazgo real que en PublicacionesProcesalesClient/
    // RamaJudicialCpnuClient (ver sus comentarios): la infraestructura del Estado colombiano
    // a veces completa la conexión TCP/TLS pero el gateway nunca responde, un cuelgue real,
    // no solo lentitud. Un segundo intento tras una pausa corta suele bastar.
    private static void pausarReintento() {
        try {
            Thread.sleep(3000);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    // true si las últimas UMBRAL_FALLOS_CONSECUTIVOS consultas de RED o HTTP 403
    // consecutivas fallaron -- señal de que SAMAI está caído o bloqueándonos ahora mismo,
    // nunca de que un radicado puntual no exista. Se reinicia solo en la siguiente consulta
    // que responda 200.
    public boolean pareceCaido() {
        return fallosConsecutivos.get() >= UMBRAL_FALLOS_CONSECUTIVOS;
    }

    private Optional<JsonNode> enviarJson(String url, String cuerpoJson) {
        HttpResponse<byte[]> response;
        try {
            response = enviarPost(url, cuerpoJson);
        } catch (Exception primerIntento) {
            pausarReintento();
            try {
                response = enviarPost(url, cuerpoJson);
            } catch (Exception segundoIntento) {
                fallosConsecutivos.incrementAndGet();
                log.warn("No se pudo consultar SAMAI: {}", segundoIntento.getMessage());
                return Optional.empty();
            }
        }
        if (response.statusCode() == 403) {
            fallosConsecutivos.incrementAndGet();
            log.warn("SAMAI respondió HTTP 403 en {}", url);
            return Optional.empty();
        }
        if (response.statusCode() != 200) {
            log.warn("SAMAI respondió HTTP {} en {}", response.statusCode(), url);
            return Optional.empty();
        }
        fallosConsecutivos.set(0);
        return Optional.of(objectMapper.readTree(new String(response.body(), StandardCharsets.UTF_8)));
    }

    private HttpResponse<byte[]> enviarPost(String url, String cuerpoJson) throws java.io.IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeoutLectura)
                .header("User-Agent", USER_AGENT)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/plain, */*");

        String cookie = cookieSesion.get();
        if (cookie != null && !cookie.isBlank()) {
            builder.header("Cookie", cookie);
        }

        HttpRequest request = builder
                .POST(HttpRequest.BodyPublishers.ofString(cuerpoJson, StandardCharsets.UTF_8))
                .build();
        // Mismo bug real confirmado en vivo (ver enviarGetSesion() arriba y el comentario de
        // PublicacionesProcesalesClient.descargarDocumento()) -- .sendAsync() + orTimeout() en
        // vez de .send() síncrono, para que el timeout se cumpla de verdad.
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

    private static String textoOVacio(JsonNode nodo) {
        String valor = nodo.asString(null);
        return valor == null ? "" : valor.strip();
    }

    private static LocalDate parsearFecha(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            // SAMAI trae fechaIngreso como "yyyy-MM-dd" puro (confirmado en vivo), a
            // diferencia del CPNU que trae fecha y hora juntas -- no hace falta parsear hora
            // aquí, nunca la trae.
            return LocalDate.parse(valor.length() > 10 ? valor.substring(0, 10) : valor, FECHA_SAMAI);
        } catch (Exception ex) {
            return null;
        }
    }
}
