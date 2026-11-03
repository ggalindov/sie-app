package sie.siejuridicos.croma;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sie.siejuridicos.caso.Caso;
import sie.siejuridicos.caso.CasoRepository;
import sie.siejuridicos.caso.RadicadoUtil;
import sie.siejuridicos.croma.dto.CromaBuzonDespachoDto;
import sie.siejuridicos.croma.dto.CromaLegalProRequest;
import sie.siejuridicos.croma.dto.CromaLegalProResponse;
import sie.siejuridicos.croma.dto.CromaMemoriaResumenDto;
import sie.siejuridicos.vigilancia.ActuacionJudicial;
import sie.siejuridicos.vigilancia.ActuacionJudicialRegistrada;
import sie.siejuridicos.vigilancia.ActuacionJudicialRegistradaRepository;
import sie.siejuridicos.vigilancia.BoletinJudicialCacheRepository;
import sie.siejuridicos.vigilancia.BoletinJudicialCacheService;
import sie.siejuridicos.vigilancia.ClasificadorActuacionFundamental;
import sie.siejuridicos.vigilancia.DocumentoProvidenciaCandidato;
import sie.siejuridicos.vigilancia.PublicacionesProcesalesClient;
import sie.siejuridicos.vigilancia.RamaJudicialCpnuClient;
import sie.siejuridicos.vigilancia.TerminoProcesalService;
import sie.siejuridicos.vigilancia.dto.ActuacionRegistradaResponse;
import sie.siejuridicos.vigilancia.dto.BoletinJudicialResponse;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// Servicio Legal-Pro impulsado por Croma API + Memoria inteligente de 7 días.
// 1. Extrae toda la información posible de Croma (expediente, actuaciones, términos, IDs relacionados,
//    código de despacho de 12 dígitos y todos los correos oficiales del juzgado).
// 2. Complementa con metadatos extendidos (ponente, ubicación física, tipo/clase/subclase/recurso) y
//    boletines/estados electrónicos en vivo.
// 3. Guarda automáticamente el informe en memoria (croma_legal_pro_cache) por 7 días exactos:
//    - Si el abogado vuelve a consultar el caso dentro de los 7 días, carga al instante sin gastar créditos.
//    - Muestra hace cuánto se guardó y cuántos días faltan para que se borre (al día 7 se purga solo).
//    - Si el abogado presiona "Actualizar Informe", vuelve a extraer en vivo y reinicia los 7 días.
@Service
public class CromaLegalProService {

    private static final Logger log = LoggerFactory.getLogger(CromaLegalProService.class);
    private static final Pattern SECUENCIA_RADICADO = Pattern.compile("(\\d{20,25})");
    private static final int DIAS_RETENCION_MEMORIA = 7;

    private final CromaClient cromaClient;
    private final CromaCreditosService creditosService;
    private final CasoRepository casoRepository;
    private final ActuacionJudicialRegistradaRepository actuacionRepository;
    private final TerminoProcesalService terminoProcesalService;
    private final BoletinJudicialCacheRepository boletinRepository;
    private final BoletinJudicialCacheService boletinCacheService;
    private final PublicacionesProcesalesClient publicacionesClient;
    private final RamaJudicialCpnuClient cpnuClient;
    private final CromaLegalProCacheRepository cacheRepository;
    private final ObjectMapper objectMapper;

    public CromaLegalProService(
            CromaClient cromaClient,
            CromaCreditosService creditosService,
            CasoRepository casoRepository,
            ActuacionJudicialRegistradaRepository actuacionRepository,
            TerminoProcesalService terminoProcesalService,
            BoletinJudicialCacheRepository boletinRepository,
            BoletinJudicialCacheService boletinCacheService,
            PublicacionesProcesalesClient publicacionesClient,
            RamaJudicialCpnuClient cpnuClient,
            CromaLegalProCacheRepository cacheRepository,
            ObjectMapper objectMapper) {
        this.cromaClient = cromaClient;
        this.creditosService = creditosService;
        this.casoRepository = casoRepository;
        this.actuacionRepository = actuacionRepository;
        this.terminoProcesalService = terminoProcesalService;
        this.boletinRepository = boletinRepository;
        this.boletinCacheService = boletinCacheService;
        this.publicacionesClient = publicacionesClient;
        this.cpnuClient = cpnuClient;
        this.cacheRepository = cacheRepository;
        this.objectMapper = objectMapper;
    }

    // Purga automática diaria a las 3:15 a.m. de informes que cumplieron sus 7 días en memoria
    @Scheduled(cron = "0 15 3 * * *")
    @Transactional
    public void purgarMemoriasExpiradas() {
        try {
            int borrados = cacheRepository.eliminarExpirados(OffsetDateTime.now());
            if (borrados > 0) {
                log.info("Legal-Pro Memoria: se borraron automáticamente {} informe(s) de Croma que cumplieron 7 días.", borrados);
            }
        } catch (Exception ex) {
            log.warn("Legal-Pro Memoria: fallo al purgar informes expirados: {}", ex.getMessage());
        }
    }

    @Transactional
    public List<CromaMemoriaResumenDto> listarMemoriasActivas() {
        OffsetDateTime ahora = OffsetDateTime.now();
        try {
            cacheRepository.eliminarExpirados(ahora);
            return cacheRepository.listarVigentes(ahora).stream()
                    .map(c -> new CromaMemoriaResumenDto(
                            c.getCasoId(),
                            c.getRadicado(),
                            c.getFechaExtraccion(),
                            c.getFechaExpiracion(),
                            calcularDiasRestantes(ahora, c.getFechaExpiracion()),
                            formatearTiempoTranscurrido(c.getFechaExtraccion(), ahora)
                    ))
                    .toList();
        } catch (Exception ex) {
            log.warn("Legal-Pro Memoria: no se pudieron listar las memorias activas: {}", ex.getMessage());
            return List.of();
        }
    }

    @Transactional
    public Optional<CromaLegalProResponse> obtenerDesdeMemoria(Long casoId, String radicadoRaw) {
        OffsetDateTime ahora = OffsetDateTime.now();
        try {
            cacheRepository.eliminarExpirados(ahora);

            Optional<CromaLegalProCache> encontrado = Optional.empty();
            if (radicadoRaw != null && !radicadoRaw.isBlank()) {
                String limpio = extraerRadicadoNumerico(radicadoRaw);
                if (!limpio.isBlank()) {
                    encontrado = cacheRepository.findByRadicado(limpio);
                }
            }
            if (encontrado.isEmpty() && casoId != null) {
                encontrado = cacheRepository.findFirstByCasoIdOrderByFechaExtraccionDesc(casoId);
            }

            if (encontrado.isEmpty()) {
                return Optional.empty();
            }

            CromaLegalProCache cache = encontrado.get();
            if (!cache.getFechaExpiracion().isAfter(ahora)) {
                cacheRepository.delete(cache);
                return Optional.empty();
            }

            CromaLegalProResponse guardado = objectMapper.readValue(cache.getPayloadJson(), CromaLegalProResponse.class);
            var cuota = creditosService.obtenerEstadoCuota();
            int diasRestantes = calcularDiasRestantes(ahora, cache.getFechaExpiracion());
            String haceTexto = formatearTiempoTranscurrido(cache.getFechaExtraccion(), ahora);

            return Optional.of(guardado.conDatosMemoria(
                    true,
                    cache.getFechaExtraccion(),
                    cache.getFechaExpiracion(),
                    diasRestantes,
                    haceTexto,
                    cuota.creditosConsumidos(),
                    cuota.limiteCreditos(),
                    cuota.consultasRestantes()
            ));
        } catch (Exception ex) {
            log.warn("Legal-Pro Memoria: fallo leyendo informe en caché: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    public CromaLegalProResponse generarInformeProfundo(CromaLegalProRequest request) {
        Caso caso = null;
        if (request.casoId() != null) {
            caso = casoRepository.buscarConClienteYAbogadoPorId(request.casoId()).orElse(null);
        }

        String radicadoBruto = request.radicado() != null && !request.radicado().isBlank()
                ? request.radicado().strip()
                : (caso != null && caso.getRadicadoId() != null ? caso.getRadicadoId() : "");

        String radicadoLimpio = extraerRadicadoNumerico(radicadoBruto);

        if (caso == null && !radicadoLimpio.isBlank()) {
            caso = casoRepository.findByRadicadoId(radicadoLimpio)
                    .flatMap(c -> casoRepository.buscarConClienteYAbogadoPorId(c.getId()))
                    .orElse(null);
        }

        String nombreCliente = caso != null ? obtenerNombreCliente(caso) : "Consulta directa por radicado";
        Long casoId = caso != null ? caso.getId() : null;
        String fuenteVisible = caso != null && caso.getFuente() != null ? caso.getFuente().getNombreVisible() : "Consulta Externa";

        var cuotaInicial = creditosService.obtenerEstadoCuota();
        if (radicadoLimpio.length() < 20) {
            return CromaLegalProResponse.error(
                    "El caso seleccionado no tiene un número de radicado judicial válido de 23 dígitos (" + radicadoBruto + "). "
                            + "Usa la Búsqueda Legal-Tech por Cédula/Nombre para descubrir primero su radicado.",
                    casoId,
                    nombreCliente,
                    radicadoBruto,
                    cuotaInicial.creditosConsumidos(),
                    cuotaInicial.limiteCreditos(),
                    cuotaInicial.consultasRestantes()
            );
        }

        // 1. Consultar en paralelo con límite estricto de tiempo (nunca bloquea la UI):
        //    a) Croma API (/co/rama-judicial/cases-by-radicado/v1) - máx 7s
        //    b) Extracción multi-proceso y multi-página profunda CPNU - máx 8s
        //    c) Correos oficiales del juzgado por código de 12 dígitos - máx 6s
        var futuroCromaExp = java.util.concurrent.CompletableFuture.supplyAsync(
                () -> cromaClient.consultarExpedientePorRadicado(radicadoLimpio))
                .completeOnTimeout(
                        CromaClient.ResultadoExpedienteCroma.error("Tiempo de espera agotado en Croma API"),
                        8, java.util.concurrent.TimeUnit.SECONDS);
        var futuroProfundo = java.util.concurrent.CompletableFuture.supplyAsync(
                () -> cpnuClient.buscarExpedienteProfundoMultiPagina(radicadoLimpio))
                .completeOnTimeout(Optional.empty(), 8, java.util.concurrent.TimeUnit.SECONDS);
        var futuroCorreosPorCodigo = java.util.concurrent.CompletableFuture.supplyAsync(
                () -> cromaClient.consultarCorreosDespachoPorRadicado(radicadoLimpio))
                .completeOnTimeout(List.of(), 6, java.util.concurrent.TimeUnit.SECONDS);

        CromaClient.ResultadoExpedienteCroma expCroma = futuroCromaExp.join();
        Optional<RamaJudicialCpnuClient.ExpedienteProfundoResuelto> expProfundoOpt = futuroProfundo.join();
        List<CromaBuzonDespachoDto> buzones = futuroCorreosPorCodigo.join();

        CromaClient.ResultadoExpedienteCroma exp = fusionarResultadosExpediente(radicadoLimpio, expCroma, expProfundoOpt);

        String despachoParaCorreo = exp.encontrado() && !exp.despacho().isBlank()
                ? exp.despacho()
                : (caso != null ? caso.getDespachoJudicial() : null);

        // 2. Si por código de 12 dígitos no hubo buzón y tenemos nombre de despacho, buscar por nombre (máx 5s)
        if (buzones.isEmpty() && despachoParaCorreo != null && !despachoParaCorreo.isBlank()) {
            buzones = java.util.concurrent.CompletableFuture.supplyAsync(
                    () -> cromaClient.consultarCorreosDespachoPorRadicado(radicadoLimpio, despachoParaCorreo))
                    .completeOnTimeout(List.of(), 5, java.util.concurrent.TimeUnit.SECONDS)
                    .join();
        }

        if (!exp.exito() && !exp.encontrado() && caso == null && buzones.isEmpty()) {
            var cuotaError = creditosService.obtenerEstadoCuota();
            return CromaLegalProResponse.error(
                    exp.mensajeError(),
                    casoId,
                    nombreCliente,
                    radicadoLimpio,
                    cuotaError.creditosConsumidos(),
                    cuotaError.limiteCreditos(),
                    cuotaError.consultasRestantes()
            );
        }

        // 3. Enriquecer metadatos extendidos (ponente, ubicación, tipo/clase/subclase) desde la extracción profunda
        if (caso != null && expProfundoOpt.isPresent()) {
            var prof = expProfundoOpt.get();
            if (prof.ponente() != null && !prof.ponente().isBlank()) caso.setPonente(prof.ponente());
            if (prof.tipoProceso() != null && !prof.tipoProceso().isBlank()) caso.setTipoProceso(prof.tipoProceso());
            if (prof.claseProceso() != null && !prof.claseProceso().isBlank()) caso.setClaseProceso(prof.claseProceso());
            if (prof.subclaseProceso() != null && !prof.subclaseProceso().isBlank()) caso.setSubclaseProceso(prof.subclaseProceso());
            if (prof.recurso() != null && !prof.recurso().isBlank()) caso.setRecursoProceso(prof.recurso());
            if (prof.ubicacion() != null && !prof.ubicacion().isBlank()) caso.setUbicacionProceso(prof.ubicacion());
            if (prof.ultimaActualizacionRama() != null) caso.setUltimaActualizacionRama(prof.ultimaActualizacionRama());
        }

        // 4. Sincronizar e inyectar en la base de datos de SIE todas las actuaciones y metadatos rescatados por Croma
        int nuevasInyectadas = 0;
        if (caso != null && exp.encontrado()) {
            if (exp.idProceso() != null) {
                caso.setIdProcesoCpnu(exp.idProceso());
            }
            if (exp.despacho() != null && !exp.despacho().isBlank()) {
                caso.setDespachoJudicial(exp.despacho());
            }
            if (exp.departamento() != null && !exp.departamento().isBlank()) {
                caso.setDepartamentoJudicial(exp.departamento());
            }
            if (exp.partes() != null && !exp.partes().isBlank()) {
                caso.setSujetosProcesales(exp.partes());
            }
            if (exp.fechaInicio() != null) {
                caso.setFechaRadicacionProceso(exp.fechaInicio());
            }
            caso.setProcesoPrivado(exp.esPrivado());

            Map<Long, ActuacionJudicialRegistrada> existentes = actuacionRepository
                    .findByCasoOrderByFechaRegistroDesc(caso)
                    .stream()
                    .collect(Collectors.toMap(
                            ActuacionJudicialRegistrada::getIdActuacionExterna,
                            r -> r,
                            (a, b) -> a));

            for (ActuacionJudicial act : exp.actuaciones()) {
                ActuacionJudicialRegistrada reg = existentes.get(act.idActuacionExterna());
                if (reg != null) {
                    boolean cambio = false;
                    if (reg.getConsecutivo() == null && act.consActuacion() != null) {
                        reg.setConsecutivo(act.consActuacion());
                        cambio = true;
                    }
                    if (reg.getFechaInicialTermino() == null && act.fechaInicialTermino() != null) {
                        reg.setFechaInicialTermino(act.fechaInicialTermino());
                        cambio = true;
                    }
                    if (reg.getFechaFinalTermino() == null && act.fechaFinalTermino() != null) {
                        reg.setFechaFinalTermino(act.fechaFinalTermino());
                        cambio = true;
                    }
                    if (cambio) {
                        actuacionRepository.save(reg);
                    }
                    terminoProcesalService.procesarSiAplica(caso.getId(), reg);
                    continue;
                }

                boolean esFund = ClasificadorActuacionFundamental.esFundamental(act);
                ActuacionJudicialRegistrada guardada = actuacionRepository.save(
                        ActuacionJudicialRegistrada.desde(caso, act, esFund));
                terminoProcesalService.procesarSiAplica(caso.getId(), guardada);
                nuevasInyectadas++;
            }

            caso.setFechaUltimoSeguimiento(LocalDateTime.now());
            caso.setUltimoSeguimientoExitoso(true);
            casoRepository.save(caso);
        }

        // 5. Construir la línea de tiempo consolidada (SIE + Croma)
        List<ActuacionRegistradaResponse> actuacionesConsolidada;
        if (caso != null) {
            actuacionesConsolidada = actuacionRepository.findByCasoOrderByFechaRegistroDesc(caso)
                    .stream()
                    .map(ActuacionRegistradaResponse::desde)
                    .toList();
        } else {
            actuacionesConsolidada = exp.actuaciones()
                    .stream()
                    .map(act -> {
                        boolean esFund = ClasificadorActuacionFundamental.esFundamental(act);
                        ActuacionJudicialRegistrada temp = ActuacionJudicialRegistrada.desde(null, act, esFund);
                        return ActuacionRegistradaResponse.desde(temp);
                    })
                    .toList();
        }

        // 6. Búsqueda profunda y caché de Boletines / Estados Electrónicos + Documentos directos CPNU
        int boletinesAntes = caso != null ? boletinRepository.listarConCasoPorCasoId(caso.getId()).size() : 0;
        List<DocumentoProvidenciaCandidato> documentosEnVivo = new ArrayList<>();

        if (request.debeBuscarBoletinesEnVivo()) {
            Map<String, DocumentoProvidenciaCandidato> porUrl = new LinkedHashMap<>();

            // 6.a y 6.b: Búsqueda de boletines y estados electrónicos acotada a los últimos boletines del mes (pedido del usuario)
            LocalDate hoy = LocalDate.now();
            LocalDate inicioMes = hoy.withDayOfMonth(1);
            LocalDate ventanaBoletines = hoy.getDayOfMonth() <= 5 ? hoy.minusDays(30) : inicioMes;

            java.util.LinkedHashSet<LocalDate> fechasEstadoDelCaso = new java.util.LinkedHashSet<>();
            for (ActuacionRegistradaResponse act : actuacionesConsolidada) {
                LocalDate fReg = act.fechaRegistro() != null ? act.fechaRegistro().toLocalDate() : null;
                LocalDate fAct = act.fechaActuacion() != null ? act.fechaActuacion().toLocalDate() : null;
                String nom = act.nombreActuacion() != null ? act.nombreActuacion().toLowerCase() : "";
                if (nom.contains("estado") || nom.contains("traslado") || nom.contains("auto") || nom.contains("sentencia") || fechasEstadoDelCaso.size() < 4) {
                    if (fReg != null && !fReg.isBefore(ventanaBoletines)) fechasEstadoDelCaso.add(fReg);
                    if (fAct != null && !fAct.isBefore(ventanaBoletines)) fechasEstadoDelCaso.add(fAct);
                }
                if (fechasEstadoDelCaso.size() >= 10) {
                    break;
                }
            }
            if (fechasEstadoDelCaso.isEmpty() && exp.fechaUltimaActuacion() != null && !exp.fechaUltimaActuacion().isBefore(ventanaBoletines)) {
                fechasEstadoDelCaso.add(exp.fechaUltimaActuacion());
            }

            List<ActuacionJudicial> actuacionesConDocs = exp.actuaciones().stream()
                    .filter(ActuacionJudicial::conDocumentos)
                    .filter(act -> {
                        LocalDate fAct = act.fechaActuacion() != null ? act.fechaActuacion().toLocalDate() : null;
                        LocalDate fReg = act.fechaRegistro() != null ? act.fechaRegistro().toLocalDate() : null;
                        LocalDate f = fReg != null ? fReg : fAct;
                        return f != null && !f.isBefore(ventanaBoletines);
                    })
                    .limit(5)
                    .toList();

            var futuroDocsCpnu = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                List<DocumentoProvidenciaCandidato> acum = new ArrayList<>();
                for (ActuacionJudicial act : actuacionesConDocs) {
                    try {
                        acum.addAll(cpnuClient.listarDocumentosDeActuacion(act.idActuacionExterna(), act.nombreActuacion()));
                    } catch (Exception ex) {
                        log.warn("Legal-Pro: fallo consultando documentos CPNU de actuación {}: {}", act.idActuacionExterna(), ex.getMessage());
                    }
                }
                return acum;
            }).completeOnTimeout(List.of(), 5, java.util.concurrent.TimeUnit.SECONDS);

            var futuroDocsPortal = java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                try {
                    return publicacionesClient.buscarTodosLosDocumentosRecientesDelProceso(radicadoLimpio, fechasEstadoDelCaso);
                } catch (Exception ex) {
                    log.warn("Legal-Pro: fallo escaneando Publicaciones Procesales para {}: {}", radicadoLimpio, ex.getMessage());
                    return List.<DocumentoProvidenciaCandidato>of();
                }
            }).completeOnTimeout(List.of(), 5, java.util.concurrent.TimeUnit.SECONDS);

            for (DocumentoProvidenciaCandidato d : futuroDocsCpnu.join()) {
                porUrl.putIfAbsent(d.urlDescarga(), d);
            }
            for (DocumentoProvidenciaCandidato d : futuroDocsPortal.join()) {
                porUrl.putIfAbsent(d.urlDescarga(), d);
            }

            List<DocumentoProvidenciaCandidato> docsParaCachear = new ArrayList<>(porUrl.values());
            if (caso != null && !docsParaCachear.isEmpty()) {
                Caso casoFinal = caso;
                // Cachear binarios PDF en segundo plano para responder de inmediato a la UI sin esperar descargas pesadas
                java.util.concurrent.CompletableFuture.runAsync(() -> {
                    try {
                        boletinCacheService.cachearDesdeConsultaManual(casoFinal, docsParaCachear);
                    } catch (Exception ex) {
                        log.warn("Legal-Pro: fallo cacheando boletines en segundo plano para caso {}: {}", casoFinal.getId(), ex.getMessage());
                    }
                });
            }

            // 6.c) Extraer enlaces directos embebidos en las anotaciones del juzgado (ej. acortar.link, micrositios)
            //      más el enlace directo al tablero de Estados Electrónicos del despacho en Publicaciones Procesales
            Pattern patronUrl = Pattern.compile("(https?://[^\\s\"'<>]+)", Pattern.CASE_INSENSITIVE);
            for (ActuacionRegistradaResponse act : actuacionesConsolidada) {
                LocalDate fReg = act.fechaRegistro() != null ? act.fechaRegistro().toLocalDate() : null;
                LocalDate fAct = act.fechaActuacion() != null ? act.fechaActuacion().toLocalDate() : null;
                LocalDate f = fReg != null ? fReg : fAct;
                if (f != null && f.isBefore(ventanaBoletines)) {
                    continue; // Acotado a los últimos boletines del mes (pedido del usuario)
                }
                if (act.anotacion() == null || !act.anotacion().toLowerCase().contains("http")) {
                    continue;
                }
                Matcher mUrl = patronUrl.matcher(act.anotacion());
                while (mUrl.find()) {
                    String link = mUrl.group(1).replaceAll("[.,;:)]+$", "");
                    porUrl.putIfAbsent(link, new DocumentoProvidenciaCandidato(
                            "Enlace en actuación (" + act.nombreActuacion() + ")",
                            link,
                            false
                    ));
                }
            }
            String urlTableroDespacho = publicacionesClient.construirUrlListadoDespacho(radicadoLimpio);
            if (urlTableroDespacho != null && !urlTableroDespacho.isBlank()) {
                porUrl.putIfAbsent(urlTableroDespacho, new DocumentoProvidenciaCandidato(
                        "Tablero Oficial de Estados Electrónicos del Despacho (" + (radicadoLimpio.length() >= 12 ? radicadoLimpio.substring(0, 12) : "") + ")",
                        urlTableroDespacho,
                        false
                ));
            }
            documentosEnVivo = new ArrayList<>(porUrl.values());
        }

        LocalDate hoyMes = LocalDate.now();
        LocalDate ventanaBoletinesCacheados = hoyMes.getDayOfMonth() <= 5 ? hoyMes.minusDays(30) : hoyMes.withDayOfMonth(1);

        List<BoletinJudicialResponse> boletinesCacheados = caso != null
                ? boletinRepository.listarConCasoPorCasoId(caso.getId())
                        .stream()
                        .filter(b -> {
                            LocalDate fDesc = b.getFechaDescarga() != null ? b.getFechaDescarga().toLocalDate() : null;
                            LocalDate fPub = b.getActuacion() != null && b.getActuacion().getFechaRegistro() != null
                                    ? b.getActuacion().getFechaRegistro().toLocalDate() : fDesc;
                            return fPub != null && !fPub.isBefore(ventanaBoletinesCacheados);
                        })
                        .map(BoletinJudicialResponse::desde)
                        .toList()
                : List.of();

        int boletinesNuevos = Math.max(0, boletinesCacheados.size() - boletinesAntes);
        int fundamentalesTotal = (int) actuacionesConsolidada.stream().filter(ActuacionRegistradaResponse::esFundamental).count();
        int terminosActivos = (int) actuacionesConsolidada.stream()
                .filter(a -> a.estadoTermino() != null && !a.estadoTermino().equals("VENCIDO"))
                .count();

        String despachoFinal = exp.encontrado() && !exp.despacho().isBlank()
                ? exp.despacho()
                : (caso != null ? caso.getDespachoJudicial() : "");
        String deptoFinal = exp.encontrado() && !exp.departamento().isBlank()
                ? exp.departamento()
                : (caso != null ? caso.getDepartamentoJudicial() : "");
        String sujetosFinal = exp.encontrado() && !exp.partes().isBlank()
                ? exp.partes()
                : (caso != null ? caso.getSujetosProcesales() : "");
        LocalDate fechaInicioFinal = exp.encontrado() && exp.fechaInicio() != null
                ? exp.fechaInicio()
                : (caso != null ? caso.getFechaRadicacionProceso() : null);
        LocalDate fechaUltimaFinal = exp.fechaUltimaActuacion();
        if (fechaUltimaFinal == null && !actuacionesConsolidada.isEmpty()) {
            var primera = actuacionesConsolidada.get(0);
            if (primera.fechaActuacion() != null) {
                fechaUltimaFinal = primera.fechaActuacion().toLocalDate();
            } else if (primera.fechaRegistro() != null) {
                fechaUltimaFinal = primera.fechaRegistro().toLocalDate();
            }
        }

        String codigoDespacho12 = !buzones.isEmpty() && buzones.get(0).codigoDespacho() != null && !buzones.get(0).codigoDespacho().isBlank()
                ? buzones.get(0).codigoDespacho()
                : (radicadoLimpio.length() >= 12 ? radicadoLimpio.substring(0, 12) : "");

        String correoCliente = caso != null && caso.getCliente() != null ? caso.getCliente().getCorreo() : null;
        String telefonoCliente = caso != null && caso.getCliente() != null ? caso.getCliente().getTelefono() : null;
        String abogadoNombre = caso != null && caso.getAbogadoAsignado() != null ? caso.getAbogadoAsignado().getNombre() : null;

        OffsetDateTime ahora = OffsetDateTime.now();
        OffsetDateTime expiraEn7Dias = ahora.plusDays(DIAS_RETENCION_MEMORIA);

        boolean encontradoFinal = exp.encontrado()
                || !actuacionesConsolidada.isEmpty()
                || (caso != null && caso.getIdProcesoCpnu() != null);

        var cuotaFinal = creditosService.obtenerEstadoCuota();
        String mensajeResumen = encontradoFinal
                ? String.format("Informe Legal-Pro guardado por 7 días: %d actuaciones totales (%d nuevas inyectadas en SIE), %d correos de juzgado y %d boletines disponibles.",
                        actuacionesConsolidada.size(), nuevasInyectadas, buzones.size(),
                        Math.max(boletinesCacheados.size(), documentosEnVivo.size()))
                : "Croma consultó la Rama Judicial pero este radicado no reporta expediente público en CPNU ordinario. Informe guardado en memoria por 7 días.";

        CromaLegalProResponse respuesta = new CromaLegalProResponse(
                true,
                mensajeResumen,
                casoId,
                nombreCliente,
                radicadoLimpio,
                fuenteVisible,
                encontradoFinal,
                exp.idProceso() != null ? exp.idProceso() : (caso != null ? caso.getIdProcesoCpnu() : null),
                exp.connectionId(),
                exp.idsProcesosRelacionados(),
                exp.soloPrivadoEnRama(),
                codigoDespacho12,
                despachoFinal,
                deptoFinal,
                sujetosFinal,
                fechaInicioFinal,
                fechaUltimaFinal,
                exp.esPrivado(),
                caso != null ? caso.getPonente() : expProfundoOpt.map(RamaJudicialCpnuClient.ExpedienteProfundoResuelto::ponente).orElse(null),
                caso != null ? caso.getTipoProceso() : expProfundoOpt.map(RamaJudicialCpnuClient.ExpedienteProfundoResuelto::tipoProceso).orElse(null),
                caso != null ? caso.getClaseProceso() : expProfundoOpt.map(RamaJudicialCpnuClient.ExpedienteProfundoResuelto::claseProceso).orElse(null),
                caso != null ? caso.getSubclaseProceso() : expProfundoOpt.map(RamaJudicialCpnuClient.ExpedienteProfundoResuelto::subclaseProceso).orElse(null),
                caso != null ? caso.getRecursoProceso() : expProfundoOpt.map(RamaJudicialCpnuClient.ExpedienteProfundoResuelto::recurso).orElse(null),
                caso != null ? caso.getUbicacionProceso() : expProfundoOpt.map(RamaJudicialCpnuClient.ExpedienteProfundoResuelto::ubicacion).orElse(null),
                null,
                caso != null ? caso.getUltimaActualizacionRama() : expProfundoOpt.map(RamaJudicialCpnuClient.ExpedienteProfundoResuelto::ultimaActualizacionRama).orElse(null),
                correoCliente,
                telefonoCliente,
                abogadoNombre,
                actuacionesConsolidada.size(),
                nuevasInyectadas,
                fundamentalesTotal,
                terminosActivos,
                Math.max(boletinesCacheados.size(), documentosEnVivo.size()),
                boletinesNuevos,
                buzones,
                boletinesCacheados,
                documentosEnVivo,
                actuacionesConsolidada,
                cuotaFinal.creditosConsumidos(),
                cuotaFinal.limiteCreditos(),
                cuotaFinal.consultasRestantes(),
                false,
                ahora,
                expiraEn7Dias,
                DIAS_RETENCION_MEMORIA,
                "hace unos segundos"
        );

        guardarEnMemoria7Dias(casoId, radicadoLimpio, respuesta, ahora, expiraEn7Dias);
        return respuesta;
    }

    private static CromaClient.ResultadoExpedienteCroma fusionarResultadosExpediente(
            String radicadoLimpio,
            CromaClient.ResultadoExpedienteCroma expCroma,
            Optional<RamaJudicialCpnuClient.ExpedienteProfundoResuelto> expProfundoOpt) {

        if (expProfundoOpt.isEmpty()) {
            return expCroma;
        }

        RamaJudicialCpnuClient.ExpedienteProfundoResuelto prof = expProfundoOpt.get();

        Long idProceso = expCroma.encontrado() && expCroma.idProceso() != null
                ? expCroma.idProceso()
                : prof.idProcesoPrincipal();

        Integer connectionId = expCroma.encontrado() && expCroma.connectionId() != null
                ? expCroma.connectionId()
                : prof.idConexion();

        java.util.LinkedHashSet<Long> idsUnicos = new java.util.LinkedHashSet<>();
        if (expCroma.idsProcesosRelacionados() != null) {
            idsUnicos.addAll(expCroma.idsProcesosRelacionados());
        }
        if (prof.idsProcesosRelacionados() != null) {
            idsUnicos.addAll(prof.idsProcesosRelacionados());
        }

        String despacho = expCroma.encontrado() && !expCroma.despacho().isBlank()
                ? expCroma.despacho()
                : prof.despacho();
        String departamento = expCroma.encontrado() && !expCroma.departamento().isBlank()
                ? expCroma.departamento()
                : prof.departamento();
        LocalDate fechaInicio = expCroma.encontrado() && expCroma.fechaInicio() != null
                ? expCroma.fechaInicio()
                : prof.fechaProceso();
        LocalDate fechaUltima = expCroma.encontrado() && expCroma.fechaUltimaActuacion() != null
                ? expCroma.fechaUltimaActuacion()
                : prof.fechaUltimaActuacion();

        java.util.LinkedHashSet<String> partesUnicas = new java.util.LinkedHashSet<>();
        if (expCroma.encontrado() && expCroma.partes() != null && !expCroma.partes().isBlank()) {
            for (String p : expCroma.partes().split("\\|")) {
                String limpio = p.replaceAll("\\s+", " ").strip();
                if (!limpio.isBlank()) partesUnicas.add(limpio);
            }
        }
        if (prof.sujetosProcesalesConsolidados() != null && !prof.sujetosProcesalesConsolidados().isBlank()) {
            for (String p : prof.sujetosProcesalesConsolidados().split("\\|")) {
                String limpio = p.replaceAll("\\s+", " ").strip();
                if (!limpio.isBlank()) partesUnicas.add(limpio);
            }
        }

        java.util.LinkedHashMap<Long, ActuacionJudicial> actuacionesUnicas = new java.util.LinkedHashMap<>();
        if (expCroma.actuaciones() != null) {
            for (ActuacionJudicial a : expCroma.actuaciones()) {
                actuacionesUnicas.putIfAbsent(a.idActuacionExterna(), a);
            }
        }
        if (prof.todasLasActuaciones() != null) {
            for (ActuacionJudicial a : prof.todasLasActuaciones()) {
                actuacionesUnicas.putIfAbsent(a.idActuacionExterna(), a);
            }
        }

        return new CromaClient.ResultadoExpedienteCroma(
                true,
                null,
                true,
                idProceso,
                connectionId,
                new ArrayList<>(idsUnicos),
                expCroma.soloPrivadoEnRama() || prof.esPrivado(),
                radicadoLimpio,
                despacho != null ? despacho : "",
                departamento != null ? departamento : "",
                fechaInicio,
                fechaUltima,
                String.join(" | ", partesUnicas),
                expCroma.esPrivado() || prof.esPrivado(),
                new ArrayList<>(actuacionesUnicas.values())
        );
    }

    private void guardarEnMemoria7Dias(
            Long casoId,
            String radicadoLimpio,
            CromaLegalProResponse respuesta,
            OffsetDateTime fechaExtraccion,
            OffsetDateTime fechaExpiracion) {
        try {
            String json = objectMapper.writeValueAsString(respuesta);
            CromaLegalProCache entidad = cacheRepository.findByRadicado(radicadoLimpio)
                    .orElseGet(CromaLegalProCache::new);
            entidad.setCasoId(casoId);
            entidad.setRadicado(radicadoLimpio);
            entidad.setPayloadJson(json);
            entidad.setFechaExtraccion(fechaExtraccion);
            entidad.setFechaExpiracion(fechaExpiracion);
            cacheRepository.save(entidad);
        } catch (Exception ex) {
            log.warn("Legal-Pro Memoria: no se pudo guardar en caché de 7 días el radicado {}: {}", radicadoLimpio, ex.getMessage());
        }
    }

    private static int calcularDiasRestantes(OffsetDateTime ahora, OffsetDateTime fechaExpiracion) {
        if (fechaExpiracion == null || !fechaExpiracion.isAfter(ahora)) {
            return 0;
        }
        long horas = Duration.between(ahora, fechaExpiracion).toHours();
        return (int) Math.max(0, Math.min(DIAS_RETENCION_MEMORIA, (horas + 23) / 24));
    }

    private static String formatearTiempoTranscurrido(OffsetDateTime desde, OffsetDateTime ahora) {
        if (desde == null) return "recientemente";
        long minutos = Math.max(0, ChronoUnit.MINUTES.between(desde, ahora));
        if (minutos < 1) {
            return "hace unos segundos";
        }
        if (minutos < 60) {
            return "hace " + minutos + (minutos == 1 ? " minuto" : " minutos");
        }
        long horas = minutos / 60;
        if (horas < 24) {
            return "hace " + horas + (horas == 1 ? " hora" : " horas");
        }
        long dias = horas / 24;
        return "hace " + dias + (dias == 1 ? " día" : " días");
    }

    private static String extraerRadicadoNumerico(String bruto) {
        String limpio = RadicadoUtil.limpiar(bruto);
        if (limpio.length() >= 20 && limpio.length() <= 25 && limpio.chars().allMatch(Character::isDigit)) {
            return limpio;
        }
        if (bruto != null) {
            Matcher m = SECUENCIA_RADICADO.matcher(bruto.replaceAll("[\\s\\-.]+", ""));
            if (m.find()) {
                return m.group(1);
            }
        }
        return limpio;
    }

    private static String obtenerNombreCliente(Caso caso) {
        if (caso.getNombreEnHoja() != null && !caso.getNombreEnHoja().isBlank()) {
            return caso.getNombreEnHoja();
        }
        if (caso.getCliente() != null && caso.getCliente().getNombre() != null) {
            return caso.getCliente().getNombre();
        }
        return "Cliente";
    }
}
