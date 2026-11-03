package sie.siejuridicos.vigilancia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sie.siejuridicos.caso.Caso;
import sie.siejuridicos.caso.CasoRepository;
import sie.siejuridicos.usuario.UsuarioInterno;
import sie.siejuridicos.vigilancia.dto.ResumenSeguimientoJudicial;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

// El motor real del seguimiento judicial 24/7: por cada caso con radicado, resuelve su
// proceso en la Rama Judicial (cacheando idProcesoCpnu en el propio Caso, ver migración
// V56), trae su historial de actuaciones EN VIVO (RamaJudicialCpnuClient) y guarda como
// NUEVAS solo las que todavía no tenía (ActuacionJudicialRegistradaRepository) -- así se
// construye con el tiempo un historial propio que permite saber qué cambió desde la última
// corrida, en vez de solo mostrar el estado actual como hace CasoService.consultar().
//
// SOLO LECTURA hacia la Rama Judicial y SOLO ESCRITURA hacia nuestra propia base de datos --
// nunca llama a EmailService ni WhatsAppService. El envío de documentos a un cliente real es
// una decisión y un módulo aparte, todavía sin construir a propósito (ver
// VigilanciaJudicialService y la memoria del proyecto).
//
// Deliberadamente SIN @Transactional envolviendo sincronizarTodos(): recorre potencialmente
// cientos de casos con una pausa real entre cada uno (ver pausaEntreCasosMs), y mantener una
// sola transacción abierta todo ese tiempo retendría una conexión de BD sin necesidad --
// mismo criterio que ya usa CasoService.enviarCorreosPendientes() para su propio lote largo.
// Cada caso se guarda en su propia operación independiente.
@Service
public class SeguimientoJudicialService {

    private static final Logger log = LoggerFactory.getLogger(SeguimientoJudicialService.class);

    private final CasoRepository casoRepository;
    private final ActuacionJudicialRegistradaRepository actuacionRepository;
    private final RamaJudicialCpnuClient cpnuClient;
    private final SamaiClient samaiClient;
    private final SeguimientoVistoUsuarioRepository seguimientoVistoUsuarioRepository;
    private final TerminoProcesalService terminoProcesalService;
    private final VigilanciaCursorSeguimientoRepository cursorRepository;
    private final long pausaEntreCasosMs;

    public SeguimientoJudicialService(CasoRepository casoRepository,
                                       ActuacionJudicialRegistradaRepository actuacionRepository,
                                       RamaJudicialCpnuClient cpnuClient,
                                       SamaiClient samaiClient,
                                       SeguimientoVistoUsuarioRepository seguimientoVistoUsuarioRepository,
                                       TerminoProcesalService terminoProcesalService,
                                       VigilanciaCursorSeguimientoRepository cursorRepository,
                                       @Value("${app.vigilancia.pausa-entre-casos-ms}") long pausaEntreCasosMs) {
        this.casoRepository = casoRepository;
        this.actuacionRepository = actuacionRepository;
        this.cpnuClient = cpnuClient;
        this.samaiClient = samaiClient;
        this.seguimientoVistoUsuarioRepository = seguimientoVistoUsuarioRepository;
        this.terminoProcesalService = terminoProcesalService;
        this.cursorRepository = cursorRepository;
        this.pausaEntreCasosMs = pausaEntreCasosMs;
    }

    private VigilanciaCursorSeguimiento cargarCursor() {
        return cursorRepository.findById((short) 1).orElseGet(() -> {
            VigilanciaCursorSeguimiento nuevo = new VigilanciaCursorSeguimiento();
            nuevo.setPosicion(0);
            return nuevo;
        });
    }

    // Pedido explícito del usuario: "si es nuevo para cada persona, si no la ha abierto le
    // sale un botoncito rojo" -- abrir la línea de tiempo de un caso ES la acción de
    // revisarlo, así que VigilanciaAdminController.lineaTiempo() llama esto cada vez que
    // responde. Upsert: una fila por (caso, usuario), se actualiza la misma fecha_vista en
    // cada apertura en vez de acumular historial (no hace falta guardar CUÁNTAS veces se
    // revisó, solo la última).
    @Transactional
    public void marcarVisto(Caso caso, UsuarioInterno usuario) {
        SeguimientoVistoUsuario visto = seguimientoVistoUsuarioRepository
                .findByCaso_IdAndUsuario_Id(caso.getId(), usuario.getId())
                .orElseGet(SeguimientoVistoUsuario::new);
        visto.setCaso(caso);
        visto.setUsuario(usuario);
        visto.setFechaVista(LocalDateTime.now());
        seguimientoVistoUsuarioRepository.save(visto);
    }

    // Resultado interno de sincronizarCaso() -- tieneProceso=false significa "no se pudo
    // resolver/usar un proceso CPNU para este caso" (radicado no válido o fuente que no usa
    // CPNU), nunca un error real: eso ya quedó marcado aparte en Caso.ultimoSeguimientoExitoso.
    // Visibilidad de paquete (no private): VigilanciaAdminController la necesita para el
    // endpoint de prueba que resuelve UN solo caso sin esperar el ciclo completo (ver
    // sincronizarCaso() más abajo, mismo motivo).
    record ResultadoCaso(boolean tieneProceso, int actuacionesNuevas, int actuacionesFundamentalesNuevas) {
    }

    public ResumenSeguimientoJudicial sincronizarTodos() {
        List<Caso> casos = casoRepository.findByRadicadoIdIsNotNull();
        int casosConProceso = 0;
        int casosSinProceso = 0;
        int actuacionesNuevas = 0;
        int actuacionesFundamentalesNuevas = 0;
        int revisados = 0;

        if (!casos.isEmpty()) {
            // Mismo bug real que ya se arregló en BoletinJudicialCacheService (ver su
            // comentario): sin esta rotación, si el CPNU corta el freno de seguridad (ver
            // pareceCaido() más abajo), los mismos primeros casos de la lista se revisan una
            // y otra vez en cada ciclo nocturno, y los últimos nunca reciben su turno.
            VigilanciaCursorSeguimiento cursor = cargarCursor();
            int inicio = Math.floorMod(cursor.getPosicion(), casos.size());
            if (inicio > 0) {
                List<Caso> rotada = new java.util.ArrayList<>(casos.subList(inicio, casos.size()));
                rotada.addAll(casos.subList(0, inicio));
                casos = rotada;
            }

            for (int i = 0; i < casos.size(); i++) {
                // Freno de seguridad real, mismo criterio que BoletinJudicialCacheService:
                // si el CPNU lleva varias consultas seguidas sin responder, seguir
                // golpeándolo durante la hora completa que tardaría terminar la lista no
                // ayuda a nadie -- se corta aquí, el siguiente ciclo retoma EXACTAMENTE
                // donde se quedó este.
                if (cpnuClient.pareceCaido()) {
                    log.warn("Seguimiento judicial: la Rama Judicial (CPNU) no está respondiendo "
                                    + "(varios fallos seguidos) -- se corta esta corrida en {}/{}, se retoma en el próximo ciclo.",
                            i, casos.size());
                    break;
                }
                Caso caso = casos.get(i);
                ResultadoCaso resultado = sincronizarCaso(caso);
                if (resultado.tieneProceso()) {
                    casosConProceso++;
                    actuacionesNuevas += resultado.actuacionesNuevas();
                    actuacionesFundamentalesNuevas += resultado.actuacionesFundamentalesNuevas();
                } else {
                    casosSinProceso++;
                }
                revisados++;
                if (i < casos.size() - 1) {
                    pausar(pausaEntreCasosMs);
                }
            }
            cursor.setPosicion(cursor.getPosicion() + revisados);
            cursorRepository.save(cursor);
        }

        // Bug real encontrado en vivo (2026-11-01, auditoría de trazabilidad): este método ya
        // NO se auto-reporta en RegistroSistema -- eso quedó aquí desde ANTES de que existiera
        // VigilanciaExternaOrquestador (cuando este servicio corría solo, con su propio
        // scheduler). Ahora el orquestador es el ÚNICO llamador (ver ejecutarPasoSeguimiento())
        // y ya registra el resultado con el contexto completo (si CPNU y/o SAMAI "parecían
        // caídos"), exitoso=true SIEMPRE aquí (sin importar el resultado real) producía una
        // segunda entrada contradictoria en /admin/registro junto a la correcta del
        // orquestador -- confuso para el admin, que veía "exitoso" y "no exitoso" para la
        // misma corrida casi al mismo segundo.

        return new ResumenSeguimientoJudicial(
                revisados, casosConProceso, casosSinProceso, actuacionesNuevas, actuacionesFundamentalesNuevas);
    }

    // Nunca lanza -- un fallo real (red, formato inesperado) queda igual de bien reflejado en
    // Caso.ultimoSeguimientoExitoso=false que cualquier otro "no se pudo", así que no hace
    // falta distinguirlos con una excepción que interrumpiría sincronizarTodos() a mitad de
    // los demás casos. Visibilidad de paquete a propósito (ver comentario de ResultadoCaso
    // arriba): "testea los reportes... asigna un radicado real" pidió resolver un único caso
    // de prueba contra el CPNU sin esperar el ciclo completo de ~364 casos.
    ResultadoCaso sincronizarCaso(Caso caso) {
        try {
            if (!sie.siejuridicos.caso.RadicadoUtil.esFormatoCpnuValido(caso.getRadicadoId())) {
                log.debug("Caso {} tiene radicado no judicial/no CPNU ('{}') -- se omite consulta externa.",
                        caso.getId(), caso.getRadicadoId());
                marcarSeguimiento(caso, true);
                return new ResultadoCaso(false, 0, 0);
            }
            String radicadoLimpio = sie.siejuridicos.caso.RadicadoUtil.limpiar(caso.getRadicadoId());
            if (!radicadoLimpio.equals(caso.getRadicadoId())) {
                caso.setRadicadoId(radicadoLimpio);
                casoRepository.save(caso);
            }
            Long idProceso = caso.getIdProcesoCpnu();
            // Gap real pedido por el usuario: "lupa nos da un seguimiento mas robusto" y
            // luego "trae absolutamente toda la informacion... entre mas datos mucho mejor"
            // -- además de idProcesoCpnu, resuelve TODO lo que da
            // RamaJudicialCpnuClient.buscarProceso() (sujetos, despacho, departamento, fecha
            // de radicación, ponente, tipo/clase/subclase de proceso, recurso, ubicación). La
            // condición incluye "getPonente() == null" (no getSujetosProcesales(), que ya se
            // llenó en una corrección anterior) a propósito: así los casos que YA tienen
            // sujetosProcesales pero no este segundo bloque más nuevo lo resuelven una vez
            // más en el próximo ciclo, sin necesitar otro backfill aparte.
            if (idProceso == null || caso.getPonente() == null) {
                Optional<RamaJudicialCpnuClient.ProcesoResuelto> resuelto =
                        cpnuClient.buscarProceso(caso.getRadicadoId());
                if (resuelto.isEmpty()) {
                    // Pedido explícito del usuario: "implementa esto... SAMAI". El CPNU es
                    // SOLO Rama Judicial ordinaria -- nunca cubre la jurisdicción
                    // contencioso-administrativa (Consejo de Estado, Tribunales
                    // Administrativos). Antes de rendirse, se intenta el mismo radicado
                    // contra SAMAI. Solo identifica el proceso (SAMAI no tiene una forma
                    // segura de traer actuaciones nuevas, ver SamaiClient) -- así que un
                    // caso resuelto por esta vía nunca pasa por el bloque de actuaciones de
                    // abajo (que necesita un idProceso del CPNU que este caso nunca va a
                    // tener): queda con su ficha básica siempre al día en cada ciclo, pero
                    // sin seguimiento de movimientos nuevos para esa jurisdicción.
                    if (caso.getIdProcesoSamai() == null) {
                        Optional<SamaiClient.ProcesoResuelto> porSamai = samaiClient.buscarProceso(caso.getRadicadoId());
                        if (porSamai.isPresent()) {
                            SamaiClient.ProcesoResuelto s = porSamai.get();
                            caso.setIdProcesoSamai(s.guid());
                            caso.setSujetosProcesales(s.sujetosProcesales());
                            caso.setDespachoJudicial(s.despacho());
                            caso.setPonente(s.ponente());
                            caso.setTipoProceso(s.tipoProceso());
                            caso.setFechaRadicacionProceso(s.fechaProceso());
                            caso.setProcesoPrivado(s.esPrivado());
                        }
                    }
                    marcarSeguimiento(caso, true);
                    return new ResultadoCaso(caso.getIdProcesoSamai() != null, 0, 0);
                }
                RamaJudicialCpnuClient.ProcesoResuelto p = resuelto.get();
                idProceso = p.idProceso();
                caso.setIdProcesoCpnu(idProceso);
                caso.setSujetosProcesales(p.sujetosProcesales());
                caso.setDespachoJudicial(p.despacho());
                caso.setDepartamentoJudicial(p.departamento());
                caso.setFechaRadicacionProceso(p.fechaProceso());
                caso.setProcesoPrivado(p.esPrivado());
                caso.setPonente(p.ponente());
                caso.setTipoProceso(p.tipoProceso());
                caso.setClaseProceso(p.claseProceso());
                caso.setSubclaseProceso(p.subclaseProceso());
                caso.setRecursoProceso(p.recurso());
                caso.setUbicacionProceso(p.ubicacion());
                caso.setUltimaActualizacionRama(p.ultimaActualizacionRama());
            }

            // Ciclo completo y profundo: si el caso aún no tiene actuaciones en BD, se recorren
            // TODAS las páginas disponibles (hasta 10 páginas / 400 actuaciones) para no dejar
            // expedientes incompletos. Si ya tiene actuaciones, se consulta la página 1 reciente.
            List<ActuacionJudicial> actuaciones = actuacionRepository.countByCaso(caso) == 0
                    ? cpnuClient.listarActuacionesTodasLasPaginas(idProceso)
                    : cpnuClient.listarActuaciones(idProceso);

            // Si el caso aún no tiene actuaciones y la conexión guardada respondió vacía/404,
            // re-consulta el radicado en CPNU para asociar la conexión activa más reciente.
            if (actuaciones.isEmpty() && actuacionRepository.countByCaso(caso) == 0) {
                Optional<RamaJudicialCpnuClient.ProcesoResuelto> fresco = cpnuClient.buscarProceso(caso.getRadicadoId());
                if (fresco.isPresent() && (idProceso == null || fresco.get().idProceso() != idProceso)) {
                    idProceso = fresco.get().idProceso();
                    caso.setIdProcesoCpnu(idProceso);
                    caso.setDespachoJudicial(fresco.get().despacho());
                    caso.setSujetosProcesales(fresco.get().sujetosProcesales());
                    caso.setDepartamentoJudicial(fresco.get().departamento());
                    caso.setPonente(fresco.get().ponente());
                    caso.setFechaRadicacionProceso(fresco.get().fechaProceso());
                    caso.setProcesoPrivado(fresco.get().esPrivado());
                    caso.setTipoProceso(fresco.get().tipoProceso());
                    caso.setClaseProceso(fresco.get().claseProceso());
                    caso.setSubclaseProceso(fresco.get().subclaseProceso());
                    caso.setRecursoProceso(fresco.get().recurso());
                    caso.setUbicacionProceso(fresco.get().ubicacion());
                    caso.setUltimaActualizacionRama(fresco.get().ultimaActualizacionRama());
                    casoRepository.save(caso);
                    actuaciones = cpnuClient.listarActuacionesTodasLasPaginas(idProceso);
                }
            }
            int nuevas = 0;
            int fundamentalesNuevas = 0;
            if (!actuaciones.isEmpty()) {
                // Mapa completo (no solo los ids) a propósito: permite, para las que YA
                // teníamos, completar consecutivo/fechaInicialTermino/fechaFinalTermino si
                // faltaban (ver migración V58 -- estos 3 campos se empezaron a capturar
                // después de que muchas actuaciones ya estuvieran guardadas). Nunca
                // sobrescribe un valor ya presente ni toca nombre/anotacion/fechas (esos sí
                // siguen la regla de "una actuación real nunca cambia de contenido").
                Map<Long, ActuacionJudicialRegistrada> registradasPorIdExterno =
                        actuacionRepository.findByCasoOrderByFechaRegistroDesc(caso).stream()
                                .collect(Collectors.toMap(ActuacionJudicialRegistrada::getIdActuacionExterna, r -> r));
                for (ActuacionJudicial actuacion : actuaciones) {
                    ActuacionJudicialRegistrada existente = registradasPorIdExterno.get(actuacion.idActuacionExterna());
                    if (existente != null) {
                        completarCamposCapturadosDespues(existente, actuacion);
                        terminoProcesalService.procesarSiAplica(caso.getId(), existente);
                        continue;
                    }
                    boolean fundamental = ClasificadorActuacionFundamental.esFundamental(actuacion);
                    ActuacionJudicialRegistrada guardada =
                            actuacionRepository.save(ActuacionJudicialRegistrada.desde(caso, actuacion, fundamental));
                    terminoProcesalService.procesarSiAplica(caso.getId(), guardada);
                    nuevas++;
                    if (fundamental) {
                        fundamentalesNuevas++;
                    }
                }
            }
            marcarSeguimiento(caso, true);
            return new ResultadoCaso(true, nuevas, fundamentalesNuevas);
        } catch (Exception ex) {
            log.warn("Fallo inesperado al sincronizar el caso {}: {}", caso.getId(), ex.getMessage());
            marcarSeguimiento(caso, false);
            return new ResultadoCaso(false, 0, 0);
        }
    }

    // Backfill de solo lectura de 3 columnas (ver migración V58) para actuaciones guardadas
    // ANTES de que este cliente empezara a capturarlas -- pedido explícito del usuario: "no
    // podemos reducir nada... toda la información de todos los casos". Solo llena huecos
    // (null -> valor real), nunca reemplaza un valor que ya existía.
    private void completarCamposCapturadosDespues(ActuacionJudicialRegistrada existente, ActuacionJudicial actuacionEnVivo) {
        boolean cambio = false;
        if (existente.getConsecutivo() == null && actuacionEnVivo.consActuacion() != null) {
            existente.setConsecutivo(actuacionEnVivo.consActuacion());
            cambio = true;
        }
        if (existente.getFechaInicialTermino() == null && actuacionEnVivo.fechaInicialTermino() != null) {
            existente.setFechaInicialTermino(actuacionEnVivo.fechaInicialTermino());
            cambio = true;
        }
        if (existente.getFechaFinalTermino() == null && actuacionEnVivo.fechaFinalTermino() != null) {
            existente.setFechaFinalTermino(actuacionEnVivo.fechaFinalTermino());
            cambio = true;
        }
        if (cambio) {
            actuacionRepository.save(existente);
        }
    }

    // Único punto de guardado de Caso en todo el ciclo (idProcesoCpnu ya puede venir
    // modificado en memoria desde sincronizarCaso() cuando se acaba de resolver) -- así cada
    // caso termina con una sola operación de escritura, no dos.
    private void marcarSeguimiento(Caso caso, boolean exitoso) {
        caso.setFechaUltimoSeguimiento(LocalDateTime.now());
        caso.setUltimoSeguimientoExitoso(exitoso);
        casoRepository.save(caso);
    }

    private void pausar(long milisegundos) {
        try {
            Thread.sleep(milisegundos);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
