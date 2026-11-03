package sie.siejuridicos.croma;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import sie.siejuridicos.croma.CromaCreditosService.TicketConsumoCroma;
import sie.siejuridicos.croma.dto.CromaBusquedaRequest;
import sie.siejuridicos.croma.dto.CromaBusquedaResponse;
import sie.siejuridicos.croma.dto.CromaBuzonDespachoDto;
import sie.siejuridicos.croma.dto.CromaCasoDto;
import sie.siejuridicos.vigilancia.ActuacionJudicial;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// Cliente de integración con la API de Croma (usecroma.com) conectado al Orquestador Multi-Key.
// Permite:
// 1. Buscar procesos judiciales en la Rama Judicial por Cédula / Nombre de persona o empresa (cases-by-entity/v1).
// 2. Legal-Pro: Resolver el expediente completo, notificaciones y actuaciones por radicado (cases-by-radicado/v1).
// 3. Legal-Pro: Resolver el directorio de correos oficiales del despacho judicial (emails-by-court/v1).
// Cuando una Key completa sus 150 consultas (1,500 créditos) o reporta agotamiento (HTTP 402/429),
// salta automáticamente a la siguiente Key hasta completar los 4,500 créditos mensuales.
@Component
public class CromaClient {

    private static final Logger log = LoggerFactory.getLogger(CromaClient.class);
    private static final DateTimeFormatter FECHA_ISO = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final int COSTO_DATASET_CREDITOS = 1;

    private final String urlBase;
    private final Duration timeout;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final CromaCreditosService creditosService;

    public record ResultadoExpedienteCroma(
            boolean exito,
            String mensajeError,
            boolean encontrado,
            Long idProceso,
            Integer connectionId,
            List<Long> idsProcesosRelacionados,
            boolean soloPrivadoEnRama,
            String radicado,
            String despacho,
            String departamento,
            LocalDate fechaInicio,
            LocalDate fechaUltimaActuacion,
            String partes,
            boolean esPrivado,
            List<ActuacionJudicial> actuaciones
    ) {
        public static ResultadoExpedienteCroma error(String msg) {
            return new ResultadoExpedienteCroma(
                    false, msg, false, null, null, List.of(), false, null, "", "", null, null, "", false, List.of());
        }
    }

    private record RespuestaHttpOrquestada(
            int statusCode,
            String body,
            TicketConsumoCroma ticketUsado
    ) {
    }

    public CromaClient(
            @Value("${app.croma.url-base:https://api.croma.run}") String urlBase,
            @Value("${app.croma.timeout-ms:60000}") long timeoutMs,
            ObjectMapper objectMapper,
            CromaCreditosService creditosService) {
        this.urlBase = urlBase.endsWith("/") ? urlBase.substring(0, urlBase.length() - 1) : urlBase;
        this.timeout = Duration.ofMillis(Math.max(30000, timeoutMs));
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        this.objectMapper = objectMapper;
        this.creditosService = creditosService;
    }

    // Ejecuta un POST contra Croma usando la Key activa del orquestador.
    // Si la Key activa responde HTTP 401/402/429 (agotamiento remoto o límite alcanzado en esa cuenta),
    // salta automáticamente a la siguiente Key disponible y reintenta de forma transparente.
    // También reintenta una vez ante timeout o 502/503/504 sin cobrar doble crédito.
    private RespuestaHttpOrquestada ejecutarPostConOrquestador(
            String endpointPath,
            Map<String, Object> payload,
            int costoCreditos) throws Exception {
        return ejecutarPostConOrquestador(endpointPath, payload, costoCreditos, this.timeout, true);
    }

    private RespuestaHttpOrquestada ejecutarPostConOrquestador(
            String endpointPath,
            Map<String, Object> payload,
            int costoCreditos,
            Duration timeoutEspecifico,
            boolean reintentarEnTimeout) throws Exception {

        TicketConsumoCroma ticket = creditosService.adquirirKeyYRegistrarConsumo(costoCreditos);
        String url = urlBase + endpointPath;
        String jsonBody = objectMapper.writeValueAsString(payload);
        int reintentosRed = 0;

        try {
            while (true) {
                HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(url))
                        .timeout(timeoutEspecifico)
                        .header("Authorization", "Bearer " + ticket.apiKey())
                        .header("Content-Type", "application/json")
                        .header("Accept", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                        .build();

                HttpResponse<byte[]> response;
                try {
                    response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
                } catch (Exception netEx) {
                    if (reintentarEnTimeout && reintentosRed < 1) {
                        reintentosRed++;
                        log.warn("Reintentando petición a Croma ({}) tras timeout/red: {}", endpointPath, netEx.getMessage());
                        Thread.sleep(1000);
                        continue;
                    }
                    throw netEx;
                }

                int status = response.statusCode();
                String bodyStr = new String(response.body(), StandardCharsets.UTF_8);

                if (status == 401 || status == 402 || status == 429) {
                    Optional<TicketConsumoCroma> siguienteTicket = creditosService.saltarASiguienteKeyPorAgotamientoRemoto(ticket);
                    if (siguienteTicket.isPresent()) {
                        ticket = siguienteTicket.get();
                        continue;
                    }
                }

                if ((status == 502 || status == 503 || status == 504) && reintentosRed < 1) {
                    reintentosRed++;
                    Thread.sleep(1500);
                    continue;
                }

                if (status != 200) {
                    creditosService.reintegrarConsumoFallido(ticket);
                }

                return new RespuestaHttpOrquestada(status, bodyStr, ticket);
            }
        } catch (Exception ex) {
            creditosService.reintegrarConsumoFallido(ticket);
            throw ex;
        }
    }

    public CromaBusquedaResponse buscarProcesosPorEntidad(CromaBusquedaRequest request) {
        var estadoCuota = creditosService.obtenerEstadoCuota();

        if (!creditosService.tieneApiKeyConfigurada()) {
            return CromaBusquedaResponse.error(
                    "No hay API Keys de Croma configuradas. Registra tus tokens en CROMA_API_KEY / CROMA_API_KEY_2 / CROMA_API_KEY_3.",
                    estadoCuota.creditosConsumidos(),
                    estadoCuota.limiteCreditos(),
                    estadoCuota.consultasRestantes());
        }

        int costoConsulta = creditosService.getCreditosPorConsulta();

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("name", request.nombre().trim());
            payload.put("entity_type", request.tipoEntidadNormalizado());
            payload.put("active_only", request.esSoloActivos());

            RespuestaHttpOrquestada resp = ejecutarPostConOrquestador(
                    "/co/rama-judicial/cases-by-entity/v1", payload, costoConsulta);

            if (resp.statusCode() != 200) {
                log.warn("Croma respondió status HTTP {} al buscar '{}': {}", resp.statusCode(), request.nombre(), resp.body());
                var cuotaPost = creditosService.obtenerEstadoCuota();
                return CromaBusquedaResponse.error(
                        "Croma respondió con código HTTP " + resp.statusCode() + ". Detalle: " + resp.body(),
                        cuotaPost.creditosConsumidos(),
                        cuotaPost.limiteCreditos(),
                        cuotaPost.consultasRestantes());
            }

            JsonNode root = objectMapper.readTree(resp.body());
            JsonNode casesArray = root.path("data").path("cases");
            if (!casesArray.isArray()) {
                casesArray = root.path("cases");
            }

            List<CromaCasoDto> listaCasos = new ArrayList<>();
            if (casesArray.isArray()) {
                for (JsonNode nodo : casesArray) {
                    String radicado = primerTextoNoVacio(nodo, "registration_number", "radicado", "case_number", "llave_proceso");
                    String despacho = primerTextoNoVacio(nodo, "court", "despacho");
                    String departamento = primerTextoNoVacio(nodo, "department", "departamento");
                    LocalDate fechaInicio = parsearFecha(primerTextoNoVacio(nodo, "start_date", "fecha_proceso"));
                    LocalDate fechaUltima = parsearFecha(primerTextoNoVacio(nodo, "last_action_date", "fecha_ultima_actuacion"));
                    String partes = extraerPartes(nodo);
                    boolean esPrivado = nodo.path("is_private").asBoolean(nodo.path("es_privado").asBoolean(false));

                    listaCasos.add(new CromaCasoDto(
                            radicado,
                            despacho,
                            departamento,
                            fechaInicio,
                            fechaUltima,
                            partes,
                            esPrivado
                    ));
                }
            }

            var cuotaPost = creditosService.obtenerEstadoCuota();
            return CromaBusquedaResponse.exito(
                    listaCasos,
                    cuotaPost.creditosConsumidos(),
                    cuotaPost.limiteCreditos(),
                    cuotaPost.consultasRestantes());

        } catch (CuotaCromaAgotadaException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Fallo de red o parseo al consultar Croma para '{}': {}", request.nombre(), ex.getMessage());
            var cuotaPost = creditosService.obtenerEstadoCuota();
            return CromaBusquedaResponse.error(
                    "Error al comunicarse con Croma API: " + ex.getMessage(),
                    cuotaPost.creditosConsumidos(),
                    cuotaPost.limiteCreditos(),
                    cuotaPost.consultasRestantes());
        }
    }

    // Legal-Pro: Resuelve el expediente completo y sus actuaciones en vivo en la Rama Judicial a través de Croma
    public ResultadoExpedienteCroma consultarExpedientePorRadicado(String radicadoLimpio) {
        if (!creditosService.tieneApiKeyConfigurada()) {
            return ResultadoExpedienteCroma.error(
                    "No hay API Keys de Croma configuradas en el orquestador.");
        }

        int costoConsulta = creditosService.getCreditosPorConsulta();

        try {
            Map<String, Object> payload = Map.of("registration_number", radicadoLimpio);
            RespuestaHttpOrquestada resp = ejecutarPostConOrquestador(
                    "/co/rama-judicial/cases-by-radicado/v1", payload, costoConsulta, Duration.ofSeconds(7), false);

            if (resp.statusCode() != 200) {
                log.warn("Croma cases-by-radicado respondió HTTP {} para {}: {}", resp.statusCode(), radicadoLimpio, resp.body());
                return ResultadoExpedienteCroma.error("Croma respondió HTTP " + resp.statusCode() + ": " + resp.body());
            }

            JsonNode root = objectMapper.readTree(resp.body());
            JsonNode data = root.path("data");
            if (data.isMissingNode() || data.isNull()) {
                data = root;
            }

            boolean found = data.path("found").asBoolean(false);
            boolean soloPrivadoEnRama = data.path("private_only").asBoolean(false);
            List<Long> idsRelacionados = new ArrayList<>();
            JsonNode caseIdsNode = data.path("case_ids");
            if (caseIdsNode.isArray()) {
                for (JsonNode cid : caseIdsNode) {
                    if (cid.isNumber()) {
                        idsRelacionados.add(cid.asLong());
                    }
                }
            }

            JsonNode primary = data.path("primary_case");
            if (!found || primary.isMissingNode() || primary.isNull()) {
                return new ResultadoExpedienteCroma(
                        true, null, false, null, null, idsRelacionados, soloPrivadoEnRama, radicadoLimpio, "", "", null, null, "", soloPrivadoEnRama, List.of());
            }

            Long idProceso = primary.path("id").isNumber() ? primary.path("id").asLong() : null;
            Integer connectionId = primary.path("connection_id").isNumber() ? primary.path("connection_id").asInt() : null;
            String radicado = primerTextoNoVacio(primary, "registration_number", "radicado");
            if (radicado.isBlank()) radicado = radicadoLimpio;
            String despacho = primerTextoNoVacio(primary, "court", "despacho");
            String departamento = primerTextoNoVacio(primary, "department", "departamento");
            LocalDate fechaInicio = parsearFecha(primerTextoNoVacio(primary, "start_date", "fecha_proceso"));
            LocalDate fechaUltima = parsearFecha(primerTextoNoVacio(primary, "last_action_date", "fecha_ultima_actuacion"));
            String partes = extraerPartes(primary);
            boolean esPrivado = primary.path("is_private").asBoolean(false) || soloPrivadoEnRama;

            List<ActuacionJudicial> actuaciones = new ArrayList<>();
            JsonNode actionsArray = data.path("actions");
            if (actionsArray.isArray()) {
                for (JsonNode actNode : actionsArray) {
                    if (!actNode.path("id").isNumber()) {
                        continue;
                    }
                    long idAct = actNode.path("id").asLong();
                    Integer sec = actNode.path("sequence").isNumber() ? actNode.path("sequence").asInt() : null;
                    LocalDateTime fechaAct = parsearFechaHora(primerTextoNoVacio(actNode, "action_date", "fecha_actuacion"));
                    String tipoAct = primerTextoNoVacio(actNode, "action_type", "actuacion");
                    String nota = primerTextoNoVacio(actNode, "note", "anotacion");
                    LocalDate fechaIniTerm = parsearFecha(primerTextoNoVacio(actNode, "start_date", "fecha_inicial"));
                    LocalDate fechaFinTerm = parsearFecha(primerTextoNoVacio(actNode, "end_date", "fecha_final"));
                    LocalDateTime fechaReg = parsearFechaHora(primerTextoNoVacio(actNode, "registered_date", "fecha_registro"));
                    if (fechaReg == null) {
                        fechaReg = fechaAct;
                    }
                    boolean conDocs = actNode.path("has_documents").asBoolean(false);

                    actuaciones.add(new ActuacionJudicial(
                            idAct,
                            fechaAct,
                            tipoAct.isBlank() ? "Actuación judicial" : tipoAct,
                            nota.isBlank() ? null : nota,
                            fechaReg,
                            conDocs,
                            sec,
                            fechaIniTerm,
                            fechaFinTerm
                    ));
                }
            }

            return new ResultadoExpedienteCroma(
                    true,
                    null,
                    true,
                    idProceso,
                    connectionId,
                    idsRelacionados,
                    soloPrivadoEnRama,
                    radicado,
                    despacho,
                    departamento,
                    fechaInicio,
                    fechaUltima,
                    partes,
                    esPrivado,
                    actuaciones
            );
        } catch (CuotaCromaAgotadaException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Fallo al consultar expediente en Croma para {}: {}", radicadoLimpio, ex.getMessage());
            return ResultadoExpedienteCroma.error("Error de red al consultar expediente en Croma: " + ex.getMessage());
        }
    }

    // Legal-Pro: Resuelve los correos oficiales del juzgado a partir del radicado (Dataset = 1 crédito)
    // Si por código de 12 dígitos no encuentra coincidencia y tenemos el nombre del despacho, busca por nombre.
    public List<CromaBuzonDespachoDto> consultarCorreosDespachoPorRadicado(String radicadoOCodigo, String nombreDespachoFallback) {
        if (!creditosService.tieneApiKeyConfigurada()) {
            return List.of();
        }

        List<CromaBuzonDespachoDto> porCodigo = consultarCorreosDespachoPorRadicado(radicadoOCodigo);
        if (!porCodigo.isEmpty() || nombreDespachoFallback == null || nombreDespachoFallback.isBlank()) {
            return porCodigo;
        }

        try {
            Map<String, Object> payload = Map.of(
                    "query", nombreDespachoFallback.trim(),
                    "per_page", 5
            );
            RespuestaHttpOrquestada resp = ejecutarPostConOrquestador(
                    "/co/rama-judicial/emails-search/v1", payload, COSTO_DATASET_CREDITOS, Duration.ofSeconds(6), false);
            if (resp.statusCode() != 200) {
                return List.of();
            }
            JsonNode root = objectMapper.readTree(resp.body());
            JsonNode results = root.path("data").path("results");
            if (!results.isArray()) {
                return List.of();
            }
            List<CromaBuzonDespachoDto> encontrados = new ArrayList<>();
            for (JsonNode m : results) {
                String email = textoOVacio(m.path("email"));
                if (email.isBlank()) continue;
                encontrados.add(new CromaBuzonDespachoDto(
                        email,
                        textoOVacio(m.path("name")),
                        textoOVacio(m.path("account_type")),
                        textoOVacio(m.path("corporation")),
                        textoOVacio(m.path("specialty")),
                        textoOVacio(m.path("department")),
                        textoOVacio(m.path("city")),
                        textoOVacio(m.path("district")),
                        textoOVacio(m.path("court_code"))
                ));
            }
            return encontrados;
        } catch (Exception ex) {
            return List.of();
        }
    }

    public List<CromaBuzonDespachoDto> consultarCorreosDespachoPorRadicado(String radicadoOCodigo) {
        if (!creditosService.tieneApiKeyConfigurada() || radicadoOCodigo == null || radicadoOCodigo.length() < 12) {
            return List.of();
        }

        try {
            Map<String, Object> payload = Map.of("court_code", radicadoOCodigo);
            RespuestaHttpOrquestada resp = ejecutarPostConOrquestador(
                    "/co/rama-judicial/emails-by-court/v1", payload, COSTO_DATASET_CREDITOS, Duration.ofSeconds(6), false);

            if (resp.statusCode() != 200) {
                return List.of();
            }

            JsonNode root = objectMapper.readTree(resp.body());
            JsonNode data = root.path("data");
            if (data.isMissingNode() || data.isNull()) {
                data = root;
            }

            JsonNode mailboxes = data.path("mailboxes");
            if (!mailboxes.isArray()) {
                return List.of();
            }

            List<CromaBuzonDespachoDto> resultado = new ArrayList<>();
            for (JsonNode m : mailboxes) {
                String email = textoOVacio(m.path("email"));
                if (email.isBlank()) continue;
                resultado.add(new CromaBuzonDespachoDto(
                        email,
                        textoOVacio(m.path("name")),
                        textoOVacio(m.path("account_type")),
                        textoOVacio(m.path("corporation")),
                        textoOVacio(m.path("specialty")),
                        textoOVacio(m.path("department")),
                        textoOVacio(m.path("city")),
                        textoOVacio(m.path("district")),
                        textoOVacio(m.path("court_code"))
                ));
            }
            return resultado;
        } catch (Exception ex) {
            log.warn("Fallo al consultar correos de despacho en Croma para {}: {}", radicadoOCodigo, ex.getMessage());
            return List.of();
        }
    }

    private static String primerTextoNoVacio(JsonNode nodo, String... campos) {
        for (String campo : campos) {
            String val = textoOVacio(nodo.path(campo));
            if (!val.isBlank()) {
                return val;
            }
        }
        return "";
    }

    private static String extraerPartes(JsonNode nodoCaso) {
        String partiesText = primerTextoNoVacio(nodoCaso, "parties_text", "parties", "sujetos_procesales");
        if (!partiesText.isBlank()) {
            return partiesText.replaceAll("\\s*\\r?\\n\\s*", " ").replaceAll("\\s{2,}", " ").strip();
        }
        JsonNode partiesNode = nodoCaso.path("parties");
        if (partiesNode.isArray()) {
            List<String> partes = new ArrayList<>();
            for (JsonNode item : partiesNode) {
                if (item.isString()) {
                    String s = item.asString("").strip();
                    if (!s.isBlank()) partes.add(s);
                } else if (item.isObject()) {
                    String rol = primerTextoNoVacio(item, "role", "tipo");
                    String nombre = primerTextoNoVacio(item, "name", "nombre");
                    if (!rol.isBlank() && !nombre.isBlank()) {
                        partes.add(rol + ": " + nombre);
                    } else if (!nombre.isBlank()) {
                        partes.add(nombre);
                    }
                }
            }
            return String.join(" | ", partes);
        }
        return "";
    }

    private static String textoOVacio(JsonNode nodo) {
        String val = nodo.asString(null);
        return val == null ? "" : val.strip();
    }

    private static LocalDate parsearFecha(String val) {
        if (val == null || val.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(val.strip().substring(0, Math.min(10, val.strip().length())), FECHA_ISO);
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalDateTime parsearFechaHora(String val) {
        if (val == null || val.isBlank()) {
            return null;
        }
        String limpio = val.strip();
        try {
            if (limpio.length() >= 19 && limpio.charAt(10) == 'T') {
                return LocalDateTime.parse(limpio.substring(0, 19));
            }
            if (limpio.length() >= 10) {
                return LocalDate.parse(limpio.substring(0, 10), FECHA_ISO).atStartOfDay();
            }
        } catch (Exception e) {
            // ignorar formato inesperado
        }
        return null;
    }
}
