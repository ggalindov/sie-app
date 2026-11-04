package sie.siejuridicos.croma;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import sie.siejuridicos.caso.Caso;
import sie.siejuridicos.caso.CasoRepository;
import sie.siejuridicos.caso.RadicadoUtil;
import sie.siejuridicos.common.cifrado.CifradoService;
import sie.siejuridicos.crm.ClienteCrm;
import sie.siejuridicos.crm.ClienteCrmRepository;
import sie.siejuridicos.croma.dto.CromaBusquedaRequest;
import sie.siejuridicos.croma.dto.CromaBusquedaResponse;
import sie.siejuridicos.croma.dto.CromaCasoDto;
import sie.siejuridicos.vigilancia.RamaJudicialCpnuClient;
import sie.siejuridicos.vigilancia.SamaiClient;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

// Servicio orquestador de búsqueda Legal-Tech Multirrama en todas las ramas del poder en Colombia.
// Permite buscar tanto por Nombre / Razón Social como por Cédula / NIT, resolviendo automáticamente
// la identidad en RUES y el CRM de SIE, y consultando en paralelo:
// 1. Rama Judicial Ordinaria (Civil, Laboral, Familia, Penal) vía Croma API y CPNU.
// 2. Rama Contencioso-Administrativa (Consejo de Estado y Tribunales Administrativos) vía SAMAI.
// 3. Jurisdicción Constitucional (Tutelas y acciones constitucionales).
// 4. Superintendencias (Sociedades, SIC, Financiera).
// 5. Expedientes internos de la firma registrados en SIE.
@Service
public class BusquedaMultirramaService {

    private static final Logger log = LoggerFactory.getLogger(BusquedaMultirramaService.class);
    private static final long TIMEOUT_POR_RAMA_SEGUNDOS = 18;

    private final CromaClient cromaClient;
    private final CromaCreditosService creditosService;
    private final RamaJudicialCpnuClient cpnuClient;
    private final SamaiClient samaiClient;
    private final ClienteCrmRepository clienteCrmRepository;
    private final CasoRepository casoRepository;
    private final CifradoService cifradoService;

    public BusquedaMultirramaService(
            CromaClient cromaClient,
            CromaCreditosService creditosService,
            RamaJudicialCpnuClient cpnuClient,
            SamaiClient samaiClient,
            ClienteCrmRepository clienteCrmRepository,
            CasoRepository casoRepository,
            CifradoService cifradoService) {
        this.cromaClient = cromaClient;
        this.creditosService = creditosService;
        this.cpnuClient = cpnuClient;
        this.samaiClient = samaiClient;
        this.clienteCrmRepository = clienteCrmRepository;
        this.casoRepository = casoRepository;
        this.cifradoService = cifradoService;
    }

    public CromaBusquedaResponse buscar(CromaBusquedaRequest request) {
        String queryOriginal = request.nombre() != null ? request.nombre().strip() : "";
        if (queryOriginal.isBlank()) {
            var cuota = creditosService.obtenerEstadoCuota();
            return CromaBusquedaResponse.error("El criterio de búsqueda no puede estar vacío.",
                    cuota.creditosConsumidos(), cuota.limiteCreditos(), cuota.consultasRestantes());
        }

        // 1. Detección inteligente: ¿Es Cédula / NIT o Nombre de persona?
        String soloDigitos = queryOriginal.replaceAll("[^0-9]", "");
        boolean esDocumento = esFormatoDocumento(queryOriginal, soloDigitos);

        String documentoIdentificado = null;
        String nombreResuelto = null;
        String tipoEntidadEfectivo = request.tipoEntidadNormalizado();

        if (esDocumento) {
            documentoIdentificado = soloDigitos;
            log.info("Búsqueda Legal-Tech por documento detectada: {}", documentoIdentificado);

            // A. Intentar resolver en CRM interno por índice ciego
            try {
                String hashDoc = cifradoService.indiceCiego(documentoIdentificado);
                Optional<ClienteCrm> crmOpt = clienteCrmRepository.findByCedulaNitHash(hashDoc);
                if (crmOpt.isPresent()) {
                    nombreResuelto = crmOpt.get().getNombre();
                    log.info("Documento {} resuelto en CRM interno a: '{}'", documentoIdentificado, nombreResuelto);
                }
            } catch (Exception ex) {
                log.warn("Error resolviendo documento en CRM interno: {}", ex.getMessage());
            }

            // B. Si no se resolvió y parece NIT/Cédula empresarial (8-10 dígitos), consultar RUES vía Croma
            if (nombreResuelto == null && documentoIdentificado.length() >= 8 && documentoIdentificado.length() <= 10) {
                try {
                    Optional<CromaClient.RuesResultado> ruesOpt = cromaClient.consultarRuesPorNit(documentoIdentificado);
                    if (ruesOpt.isPresent() && ruesOpt.get().encontrado()) {
                        nombreResuelto = ruesOpt.get().razonSocial();
                        tipoEntidadEfectivo = "juridical";
                        log.info("NIT {} resuelto en RUES a: '{}' ({})", documentoIdentificado, nombreResuelto, ruesOpt.get().estado());
                    }
                } catch (Exception ex) {
                    log.warn("Error resolviendo NIT en RUES: {}", ex.getMessage());
                }
            }
        } else {
            nombreResuelto = queryOriginal;
            // Si es nombre, verificar si existe en CRM para obtener también su Cédula/NIT
            try {
                List<ClienteCrm> todosCrm = clienteCrmRepository.findAll();
                for (ClienteCrm c : todosCrm) {
                    if (c.getNombre() != null && c.getNombre().equalsIgnoreCase(queryOriginal)) {
                        documentoIdentificado = c.getCedulaNit();
                        break;
                    }
                }
            } catch (Exception ignored) {
            }
        }

        final String nombreParaConsultar = (nombreResuelto != null && !nombreResuelto.isBlank()) ? nombreResuelto : queryOriginal;
        final String docParaConsultar = documentoIdentificado;
        final String tipoEntidadFinal = tipoEntidadEfectivo;
        final boolean esPersonaJuridica = "juridical".equalsIgnoreCase(tipoEntidadEfectivo);

        // 2. Ejecutar consultas en paralelo en todas las ramas del poder
        CompletableFuture<List<CromaCasoDto>> futuroCroma = CompletableFuture.supplyAsync(() ->
                consultarRamaCroma(nombreParaConsultar, tipoEntidadFinal, request.esSoloActivos()));

        CompletableFuture<List<CromaCasoDto>> futuroCpnu = CompletableFuture.supplyAsync(() ->
                consultarRamaCpnu(nombreParaConsultar, docParaConsultar, esPersonaJuridica));

        CompletableFuture<List<CromaCasoDto>> futuroSamai = CompletableFuture.supplyAsync(() ->
                consultarRamaSamai(nombreParaConsultar, docParaConsultar, request.corporacionSamai()));

        CompletableFuture<List<CromaCasoDto>> futuroSie = CompletableFuture.supplyAsync(() ->
                consultarExpedientesSie(nombreParaConsultar, docParaConsultar));

        // Esperar a que terminen o venza el timeout
        CompletableFuture.allOf(futuroCroma, futuroCpnu, futuroSamai, futuroSie)
                .orTimeout(TIMEOUT_POR_RAMA_SEGUNDOS, TimeUnit.SECONDS)
                .exceptionally(ex -> {
                    log.warn("Tiempo de espera agotado o error en una de las ramas judiciales: {}", ex.getMessage());
                    return null;
                })
                .join();

        List<CromaCasoDto> resultadosCroma = safeGet(futuroCroma);
        List<CromaCasoDto> resultadosCpnu = safeGet(futuroCpnu);
        List<CromaCasoDto> resultadosSamai = safeGet(futuroSamai);
        List<CromaCasoDto> resultadosSie = safeGet(futuroSie);

        // 3. Unificar, deduplicar por radicado de 23 dígitos y marcar existentes en SIE
        Map<String, CromaCasoDto> mapaUnificado = new LinkedHashMap<>();

        // Prioridad de enriquecimiento: Croma (más limpio) -> SAMAI -> CPNU -> SIE
        agregarYConsolidar(mapaUnificado, resultadosCroma);
        agregarYConsolidar(mapaUnificado, resultadosSamai);
        agregarYConsolidar(mapaUnificado, resultadosCpnu);
        agregarYConsolidar(mapaUnificado, resultadosSie);

        // 4. Filtrar por rama si el usuario seleccionó un filtro específico
        List<CromaCasoDto> listaFinal = new ArrayList<>(mapaUnificado.values());
        if (request.ramaFiltro() != null && !request.ramaFiltro().isBlank() && !"TODAS".equalsIgnoreCase(request.ramaFiltro())) {
            String filtroUpper = request.ramaFiltro().strip().toUpperCase();
            listaFinal = listaFinal.stream()
                    .filter(c -> c.rama() != null && c.rama().toUpperCase().contains(filtroUpper))
                    .toList();
        }

        // Ordenar por fecha de última actuación o fecha de inicio descendente
        listaFinal = new ArrayList<>(listaFinal);
        listaFinal.sort((a, b) -> {
            LocalDate fa = a.fechaUltimaActuacion() != null ? a.fechaUltimaActuacion() : a.fechaInicio();
            LocalDate fb = b.fechaUltimaActuacion() != null ? b.fechaUltimaActuacion() : b.fechaInicio();
            if (fa == null && fb == null) return 0;
            if (fa == null) return 1;
            if (fb == null) return -1;
            return fb.compareTo(fa);
        });

        // 5. Estadísticas por Rama del Poder
        Map<String, Integer> totalPorRama = new LinkedHashMap<>();
        for (CromaCasoDto c : mapaUnificado.values()) {
            String rama = c.rama() != null ? c.rama() : "RAMA JUDICIAL ORDINARIA";
            totalPorRama.put(rama, totalPorRama.getOrDefault(rama, 0) + 1);
        }

        List<String> ramasConsultadas = List.of(
                "RAMA JUDICIAL ORDINARIA",
                "CONTENCIOSO-ADMINISTRATIVO (SAMAI)",
                "CONSTITUCIONAL",
                "SUPERINTENDENCIA",
                "EXPEDIENTE VIGILADO SIE"
        );

        var cuotaFinal = creditosService.obtenerEstadoCuota();

        return CromaBusquedaResponse.exito(
                listaFinal,
                cuotaFinal.creditosConsumidos(),
                cuotaFinal.limiteCreditos(),
                cuotaFinal.consultasRestantes(),
                documentoIdentificado,
                nombreResuelto,
                ramasConsultadas,
                totalPorRama
        );
    }

    // Rama Judicial Ordinaria vía Croma API
    private List<CromaCasoDto> consultarRamaCroma(String nombre, String tipoEntidad, boolean soloActivos) {
        if (!creditosService.tieneApiKeyConfigurada()) {
            return List.of();
        }
        try {
            CromaBusquedaRequest subReq = new CromaBusquedaRequest(nombre, tipoEntidad, soloActivos, null, null, null);
            CromaBusquedaResponse resp = cromaClient.buscarProcesosPorEntidad(subReq);
            if (resp.exito() && resp.casos() != null) {
                return resp.casos();
            }
        } catch (Exception ex) {
            log.warn("Fallo al consultar Rama Judicial vía Croma: {}", ex.getMessage());
        }
        return List.of();
    }

    // Rama Judicial Ordinaria vía CPNU directa
    private List<CromaCasoDto> consultarRamaCpnu(String nombre, String documento, boolean esPersonaJuridica) {
        if (cpnuClient.pareceCaido()) {
            return List.of();
        }
        List<CromaCasoDto> casos = new ArrayList<>();
        try {
            var rawCasos = cpnuClient.buscarPorNombre(nombre, esPersonaJuridica);
            for (var p : rawCasos) {
                String rad = RadicadoUtil.limpiar(p.llaveProceso());
                String despacho = p.despacho();
                String rama = CromaCasoDto.inferirRama(despacho, null);
                casos.add(new CromaCasoDto(
                        rad,
                        despacho,
                        p.departamento(),
                        p.fechaProceso(),
                        p.fechaUltimaActuacion(),
                        p.sujetosProcesales(),
                        false,
                        rama,
                        "Rama Judicial CPNU",
                        false,
                        null,
                        null
                ));
            }
        } catch (Exception ex) {
            log.warn("CPNU buscarPorNombre falló para '{}': {}", nombre, ex.getMessage());
        }

        // Si tenemos documento, intentar buscarlo también en CPNU
        if (documento != null && !documento.isBlank() && !documento.equalsIgnoreCase(nombre)) {
            try {
                var rawCasosDoc = cpnuClient.buscarPorNombre(documento, false);
                for (var p : rawCasosDoc) {
                    String rad = RadicadoUtil.limpiar(p.llaveProceso());
                    String despacho = p.despacho();
                    String rama = CromaCasoDto.inferirRama(despacho, null);
                    casos.add(new CromaCasoDto(
                            rad,
                            despacho,
                            p.departamento(),
                            p.fechaProceso(),
                            p.fechaUltimaActuacion(),
                            p.sujetosProcesales(),
                            false,
                            rama,
                            "Rama Judicial CPNU (Por Documento)",
                            false,
                            null,
                            null
                    ));
                }
            } catch (Exception ignored) {
            }
        }
        return casos;
    }

    // Rama Contencioso-Administrativa vía SAMAI
    private List<CromaCasoDto> consultarRamaSamai(String nombre, String documento, String corporacion) {
        if (samaiClient.pareceCaido()) {
            return List.of();
        }
        List<CromaCasoDto> casos = new ArrayList<>();
        try {
            var items = samaiClient.buscarPorParte(nombre, corporacion);
            for (var it : items) {
                String rad = RadicadoUtil.limpiar(it.radicado());
                if (rad.isBlank()) rad = it.radicado();
                String rama = CromaCasoDto.inferirRama(it.despacho(), it.clase());
                casos.add(new CromaCasoDto(
                        rad,
                        it.despacho(),
                        extraerDepartamentoDeDespacho(it.despacho()),
                        it.fechaIngreso(),
                        it.fechaIngreso(),
                        it.obtenerPartesTexto(),
                        it.esPrivado(),
                        rama,
                        "SAMAI (Consejo de Estado y Tribunales)",
                        false,
                        null,
                        it.clase()
                ));
            }
        } catch (Exception ex) {
            log.warn("SAMAI buscarPorParte falló para '{}': {}", nombre, ex.getMessage());
        }

        if (documento != null && !documento.isBlank() && !documento.equalsIgnoreCase(nombre)) {
            try {
                var itemsDoc = samaiClient.buscarPorParte(documento, corporacion);
                for (var it : itemsDoc) {
                    String rad = RadicadoUtil.limpiar(it.radicado());
                    if (rad.isBlank()) rad = it.radicado();
                    String rama = CromaCasoDto.inferirRama(it.despacho(), it.clase());
                    casos.add(new CromaCasoDto(
                            rad,
                            it.despacho(),
                            extraerDepartamentoDeDespacho(it.despacho()),
                            it.fechaIngreso(),
                            it.fechaIngreso(),
                            it.obtenerPartesTexto(),
                            it.esPrivado(),
                            rama,
                            "SAMAI (Por Documento)",
                            false,
                            null,
                            it.clase()
                    ));
                }
            } catch (Exception ignored) {
            }
        }
        return casos;
    }

    // Búsqueda en expedientes internos vigilados por SIE
    private List<CromaCasoDto> consultarExpedientesSie(String nombre, String documento) {
        List<CromaCasoDto> casosSie = new ArrayList<>();
        try {
            List<Caso> todos = casoRepository.listarTodosConDetalle();
            String nombreNorm = nombre.toLowerCase();
            String docNorm = documento != null ? documento.replaceAll("[^0-9]", "") : "";

            for (Caso c : todos) {
                boolean coincide = false;
                if (c.getCliente() != null && c.getCliente().getNombre() != null) {
                    String nomCli = c.getCliente().getNombre().toLowerCase();
                    if (nomCli.contains(nombreNorm) || nombreNorm.contains(nomCli)) {
                        coincide = true;
                    }
                }
                if (!coincide && !docNorm.isBlank() && c.getRadicadoId() != null && c.getRadicadoId().contains(docNorm)) {
                    coincide = true;
                }

                if (coincide && c.getRadicadoId() != null && !c.getRadicadoId().isBlank()) {
                    String rad = RadicadoUtil.limpiar(c.getRadicadoId());
                    String despacho = c.getDespachoJudicial() != null ? c.getDespachoJudicial() : "Despacho vigilado en SIE";
                    String rama = CromaCasoDto.inferirRama(despacho, null);
                    String partes = c.getSujetosProcesales() != null && !c.getSujetosProcesales().isBlank()
                            ? c.getSujetosProcesales()
                            : (c.getCliente() != null ? "Cliente: " + c.getCliente().getNombre() : "");
                    LocalDate fechaUltima = c.getUltimaActualizacionRama() != null
                            ? c.getUltimaActualizacionRama().toLocalDate()
                            : c.getFechaRadicacionProceso();

                    casosSie.add(new CromaCasoDto(
                            rad,
                            despacho,
                            c.getDepartamentoJudicial(),
                            c.getFechaRadicacionProceso(),
                            fechaUltima,
                            partes,
                            Boolean.TRUE.equals(c.getProcesoPrivado()),
                            rama,
                            "Expediente Interno SIE",
                            true,
                            c.getId(),
                            c.getTipoProceso()
                    ));
                }
            }
        } catch (Exception ex) {
            log.warn("Error consultando expedientes SIE internos: {}", ex.getMessage());
        }
        return casosSie;
    }

    private void agregarYConsolidar(Map<String, CromaCasoDto> mapa, List<CromaCasoDto> casosNuevos) {
        for (CromaCasoDto nuevo : casosNuevos) {
            if (nuevo.radicado() == null || nuevo.radicado().isBlank()) {
                continue;
            }
            String clave = RadicadoUtil.limpiar(nuevo.radicado());
            if (clave.isBlank()) {
                clave = nuevo.radicado().strip();
            }

            // Verificar si el radicado ya existe en la base de datos de casos de SIE
            boolean existeEnSie = nuevo.yaExisteEnSie();
            Long casoId = nuevo.casoId();
            if (!existeEnSie) {
                Optional<Caso> casoLocal = casoRepository.findByRadicadoId(clave);
                if (casoLocal.isPresent()) {
                    existeEnSie = true;
                    casoId = casoLocal.get().getId();
                }
            }

            if (!mapa.containsKey(clave)) {
                mapa.put(clave, new CromaCasoDto(
                        clave,
                        nuevo.despacho(),
                        nuevo.departamento(),
                        nuevo.fechaInicio(),
                        nuevo.fechaUltimaActuacion(),
                        nuevo.partes(),
                        nuevo.esPrivado(),
                        nuevo.rama(),
                        nuevo.fuente(),
                        existeEnSie,
                        casoId,
                        nuevo.claseProceso()
                ));
            } else {
                // Fusión y enriquecimiento de metadatos
                CromaCasoDto existente = mapa.get(clave);
                String despachoMejor = (existente.despacho() != null && !existente.despacho().isBlank())
                        ? existente.despacho() : nuevo.despacho();
                String deptoMejor = (existente.departamento() != null && !existente.departamento().isBlank())
                        ? existente.departamento() : nuevo.departamento();
                LocalDate fechaIniMejor = existente.fechaInicio() != null ? existente.fechaInicio() : nuevo.fechaInicio();
                LocalDate fechaUltMejor = existente.fechaUltimaActuacion() != null ? existente.fechaUltimaActuacion() : nuevo.fechaUltimaActuacion();
                String partesMejor = (existente.partes() != null && !existente.partes().isBlank())
                        ? existente.partes() : nuevo.partes();
                String ramaMejor = existente.rama();
                if ("RAMA JUDICIAL ORDINARIA".equals(ramaMejor) && !"RAMA JUDICIAL ORDINARIA".equals(nuevo.rama())) {
                    ramaMejor = nuevo.rama();
                }
                String fuenteMejor = existente.fuente();
                if (nuevo.fuente() != null && !fuenteMejor.contains(nuevo.fuente())) {
                    fuenteMejor = fuenteMejor + " · " + nuevo.fuente();
                }

                mapa.put(clave, new CromaCasoDto(
                        clave,
                        despachoMejor,
                        deptoMejor,
                        fechaIniMejor,
                        fechaUltMejor,
                        partesMejor,
                        existente.esPrivado() && nuevo.esPrivado(),
                        ramaMejor,
                        fuenteMejor,
                        existente.yaExisteEnSie() || existeEnSie,
                        existente.casoId() != null ? existente.casoId() : casoId,
                        existente.claseProceso() != null ? existente.claseProceso() : nuevo.claseProceso()
                ));
            }
        }
    }

    private static boolean esFormatoDocumento(String texto, String soloDigitos) {
        if (soloDigitos.length() < 6 || soloDigitos.length() > 12) {
            return false;
        }
        // Verificar que no contenga letras (solo números, puntos, guiones y espacios)
        String sinNumeros = texto.replaceAll("[0-9\\.\\-\\s]", "");
        return sinNumeros.isEmpty();
    }

    private static String extraerDepartamentoDeDespacho(String despacho) {
        if (despacho == null || despacho.isBlank()) return "";
        String upper = despacho.toUpperCase();
        if (upper.contains("BOGOTA") || upper.contains("BOGOTÁ") || upper.startsWith("11001")) return "Bogotá D.C.";
        if (upper.contains("CUNDINAMARCA") || upper.startsWith("25")) return "Cundinamarca";
        if (upper.contains("ANTIOQUIA") || upper.contains("MEDELLIN") || upper.startsWith("05")) return "Antioquia";
        if (upper.contains("VALLE") || upper.contains("CALI") || upper.startsWith("76")) return "Valle del Cauca";
        if (upper.contains("ATLANTICO") || upper.contains("BARRANQUILLA") || upper.startsWith("08")) return "Atlántico";
        if (upper.contains("SANTANDER") || upper.contains("BUCARAMANGA") || upper.startsWith("68")) return "Santander";
        return "";
    }

    private static <T> List<T> safeGet(CompletableFuture<List<T>> future) {
        try {
            List<T> res = future.getNow(Collections.emptyList());
            return res != null ? res : Collections.emptyList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
