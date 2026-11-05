package sie.siejuridicos.vigilancia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sie.siejuridicos.caso.Caso;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import sie.siejuridicos.caso.RadicadoUtil;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

// Pedido explícito del usuario (2026-11-04): "el sistema debe guardarlos en el servidor al
// menos por 3 meses" -- ampliado a 90 días (antes 30 días, y originalmente 7). Corre en un ciclo PROPIO,
// separado del de SeguimientoJudicialService a propósito -- meter esto dentro de ese ciclo
// multiplicaría las llamadas a Publicaciones Procesales por cada actuación nueva de cada
// caso, justo el patrón de exceso de tráfico que ya causó un bloqueo real esta misma sesión.
// Aquí también se pausa entre cada consulta (ver pausaEntreConsultasMs), nunca en ráfaga.
@Service
public class BoletinJudicialCacheService {

    private static final Logger log = LoggerFactory.getLogger(BoletinJudicialCacheService.class);
    private static final int DIAS_VENCIMIENTO_CACHE = 90;
    // Mutex real, no cosmético: recorrer ~362 casos tarda del orden de una hora, y sin esto
    // un admin que hace doble clic en "Buscar boletines de todos los casos" (o un reinicio en
    // caliente de Spring DevTools, que detuve en vivo esta misma sesión mientras probaba este
    // código: el hilo @Async de la corrida anterior siguió vivo porque Spring solo deja de
    // ACEPTAR tareas nuevas al cerrar el executor, nunca interrumpe la que ya está en curso)
    // termina con dos corridas completas pegándole a Publicaciones Procesales al mismo
    // tiempo -- justo el patrón de ráfaga que ya causó un bloqueo real esta misma sesión.
    private final AtomicBoolean sincronizacionEnCurso = new AtomicBoolean(false);

    // Progreso en vivo SOLO del paso de boletines (pedido del usuario: barra permanente en
    // /admin/boletines con el porcentaje del ciclo de boletines, no del ciclo completo).
    // Vive en memoria: es un indicador de la corrida actual, no un dato a conservar.
    private final AtomicInteger progresoTotal = new AtomicInteger(0);
    private final AtomicInteger progresoRevisadas = new AtomicInteger(0);
    private final AtomicInteger progresoNuevos = new AtomicInteger(0);
    private volatile LocalDateTime progresoInicio = null;
    private volatile LocalDateTime progresoFin = null;
    // Segunda etapa del ciclo: reintento de lo que fallo en la primera pasada (ver
    // recuperarPendientes). fase: EN_ESPERA / PASADA / REINTENTOS.
    private volatile String progresoFase = "EN_ESPERA";
    private final AtomicInteger reintentosTotal = new AtomicInteger(0);
    private final AtomicInteger reintentosHechos = new AtomicInteger(0);
    private final AtomicInteger recuperados = new AtomicInteger(0);

    public record ProgresoBoletines(boolean enCurso, String fase, int total, int revisadas, int nuevos,
                                    int porcentaje, LocalDateTime inicio, LocalDateTime fin,
                                    int reintentosTotal, int reintentosHechos, int recuperados,
                                    long pendientes) {
    }

    public ProgresoBoletines progreso() {
        int total = progresoTotal.get();
        int revisadas = progresoRevisadas.get();
        int porcentaje = total <= 0 ? 0 : Math.min(100, (int) Math.round(revisadas * 100.0 / total));
        return new ProgresoBoletines(sincronizacionEnCurso.get(), progresoFase, total, revisadas,
                progresoNuevos.get(), porcentaje, progresoInicio, progresoFin,
                reintentosTotal.get(), reintentosHechos.get(), recuperados.get(), contarPendientes());
    }

    // Boletines/busquedas que el ciclo no logro completar y siguen en cola (ver V76).
    public long contarPendientes() {
        try {
            return pendienteRepository.countByAgotadoFalse();
        } catch (Exception ex) {
            return 0;
        }
    }

    // Bug real encontrado en vivo (pedido del usuario: "no puede ser posible que
    // unicamente encuentre el boletin de un solo caso... debemos traer todos los boletines
    // disponibles de todos los casos"): el freno de seguridad contra Publicaciones
    // Procesales caído (ver pareceCaido() en PublicacionesProcesalesClient) corta la
    // corrida apenas se topa con varios fallos seguidos -- pero como antes SIEMPRE se
    // empezaba desde el principio de la lista, si los primeros 5-10 casos ya alcanzaban
    // para disparar el freno, esos mismos 5-10 se revisaban una y otra vez EN CADA CICLO, y
    // los otros cientos de casos nunca llegaban a revisarse ni una sola vez. Este cursor
    // rota el punto de partida: cada ciclo arranca donde terminó el anterior (haya sido por
    // agotar la lista o por el freno de seguridad), así que con el tiempo todos los casos
    // reciben su turno, nunca solo los primeros de la lista.
    //
    // Persistido en BD (ver VigilanciaCursorBoletines, migración V71) desde el 2026-11-01 --
    // bug real confirmado esa misma noche: un AtomicInteger en memoria se resetea a 0 en
    // cualquier reinicio del backend (frecuente en desarrollo, cualquier despliegue en
    // producción), perdiendo todo el avance acumulado y volviendo a empezar siempre por los
    // mismos primeros casos si el circuit breaker corta seguido.
    private final VigilanciaCursorBoletinesRepository cursorRepository;

    private final ActuacionJudicialRegistradaRepository actuacionRepository;
    private final BoletinJudicialCacheRepository boletinRepository;
    private final PublicacionesProcesalesClient publicacionesProcesalesClient;
    private final RamaJudicialCpnuClient cpnuClient;
    private final long pausaEntreConsultasMs;

    private final BoletinPendienteRepository pendienteRepository;
    private final CorreoDespachoJudicialService correoDespachoJudicialService;

    public BoletinJudicialCacheService(ActuacionJudicialRegistradaRepository actuacionRepository,
                                        BoletinJudicialCacheRepository boletinRepository,
                                        VigilanciaCursorBoletinesRepository cursorRepository,
                                        BoletinPendienteRepository pendienteRepository,
                                        PublicacionesProcesalesClient publicacionesProcesalesClient,
                                        RamaJudicialCpnuClient cpnuClient,
                                        CorreoDespachoJudicialService correoDespachoJudicialService,
                                        @Value("${app.vigilancia.boletin-pausa-entre-casos-ms}") long pausaEntreConsultasMs) {
        this.actuacionRepository = actuacionRepository;
        this.boletinRepository = boletinRepository;
        this.cursorRepository = cursorRepository;
        this.pendienteRepository = pendienteRepository;
        this.publicacionesProcesalesClient = publicacionesProcesalesClient;
        this.cpnuClient = cpnuClient;
        this.correoDespachoJudicialService = correoDespachoJudicialService;
        this.pausaEntreConsultasMs = pausaEntreConsultasMs;
    }

    private VigilanciaCursorBoletines cargarCursor() {
        return cursorRepository.findById((short) 1).orElseGet(() -> {
            VigilanciaCursorBoletines nuevo = new VigilanciaCursorBoletines();
            nuevo.setPosicion(0);
            return nuevo;
        });
    }

    // Pedido explícito del usuario: "busca todos los boletines y cargalos en el sistema" --
    // disparo manual desde el panel (BoletinJudicialScheduler.digestDiario() ya la llama sola
    // todos los días a las 7am). @Async porque recorrer ~362 casos con una pausa real entre
    // cada uno (ver pausaEntreConsultasMs) tarda del orden de una hora: el admin no debería
    // quedarse con la petición HTTP colgada todo ese tiempo esperando una respuesta.
    @Async
    public void sincronizarNuevosBoletinesAsync() {
        sincronizarNuevosBoletines();
    }

    // Llamado por BoletinJudicialScheduler, antes de componer cualquier digest -- así los
    // correos de las 7am siempre tienen los boletines más frescos posibles disponibles.
    // Pedido explícito del usuario: "busca todos los boletines por cada caso" -- cubre TODOS
    // los casos con al menos una actuación con fecha (ver
    // ActuacionJudicialRegistradaRepository.ultimaActuacionConFechaPorCaso()), no solo los de
    // una ventana de tiempo reciente.
    public void sincronizarNuevosBoletines() {
        if (!sincronizacionEnCurso.compareAndSet(false, true)) {
            log.info("Cache de boletines judiciales: ya hay una corrida en curso, se ignora esta solicitud.");
            return;
        }
        try {
            sincronizarNuevosBoletinesInterno();
        } finally {
            sincronizacionEnCurso.set(false);
        }
    }

    // Solo la etapa de reintentos (sin recorrer de nuevo todas las actuaciones): la usa el
    // endpoint manual y sirve para vaciar la cola de pendientes sin esperar un ciclo completo.
    // Mismo mutex que el ciclo, para no pegarle al portal en paralelo.
    public void reintentarPendientes() {
        if (!sincronizacionEnCurso.compareAndSet(false, true)) {
            log.info("Cache de boletines judiciales: ya hay una corrida en curso, no se reintentan pendientes ahora.");
            return;
        }
        try {
            progresoInicio = LocalDateTime.now();
            progresoFin = null;
            progresoNuevos.set(0);
            recuperados.set(0);
            recuperarPendientes();
        } finally {
            progresoFase = "EN_ESPERA";
            progresoFin = LocalDateTime.now();
            sincronizacionEnCurso.set(false);
        }
    }

    // Dias hacia atras de actuaciones que se buscan en el portal ademas de la ultima de cada
    // caso. Subido de 15 a 30 (pedido explicito del usuario, 2026-11-04: "traer absolutamente
    // todos los docuemtos del ultimo mes"): para el canal heuristico de Publicaciones
    // Procesales el portal solo expone sus boletines mas recientes (una ventana mayor no
    // encuentra nada nuevo ahi), pero esta misma lista de objetivos es la que alimenta
    // tambien el canal EXACTO de la CPNU (ver procesarActuacion), que SI puede resolver el
    // documento de una actuacion de hace varias semanas -- una ventana de solo 15 dias le
    // negaba la oportunidad de intentarlo para actuaciones un poco mas viejas.
    private static final int DIAS_VENTANA_BUSQUEDA = 30;
    // Rondas de reintento al terminar la pasada principal, con espera entre rondas para que el
    // portal se recupere de una racha de fallos (la del 2026-11-01 duro una hora).
    private static final int MAX_RONDAS_RECUPERACION = 3;
    private static final long ESPERA_ENTRE_RONDAS_MS = 120_000L;
    // Tras este numero de intentos acumulados (entre ciclos) un pendiente se marca "agotado" y
    // deja de reintentarse: evita insistir para siempre con un documento que no aparece.
    private static final int MAX_INTENTOS_PENDIENTE = 12;

    // Las ultimas actuaciones de cada caso y las de los ultimos DIAS_VENTANA_BUSQUEDA dias, sin
    // repetir la MISMA actuacion dos veces (puede aparecer en ambas consultas de origen).
    //
    // CORREGIDO -- bug real encontrado en auditoria del 2026-11-04 (el usuario reporto "no esta
    // trayendo todas las actuaciones ni todos los autos"): la version anterior deduplicaba por
    // (idCaso, fecha), no por actuacion -- si un despacho publicaba DOS O MAS actuaciones el
    // mismo dia para el mismo caso (muy comun: un auto de fondo y su fijacion en estado el mismo
    // dia, por ejemplo), solo la primera de esa pareja entraba a procesarActuacion() y las demas
    // se descartaban en silencio, SIN buscarles documento por ningun canal (ni el exacto de CPNU
    // ni el heuristico de Publicaciones Procesales). Confirmado en produccion: de 233 actuaciones
    // en la ventana de 30 dias, solo 158 parejas (caso, dia) unicas se procesaban -- 75
    // actuaciones reales nunca se consultaban. Deduplicar por el id propio de la actuacion
    // (en vez de por caso+fecha) procesa cada una por separado sin perder ninguna, a costa de
    // alguna consulta heuristica repetida al portal para el mismo (caso, dia) cuando hay varias
    // actuaciones ese dia -- aceptable: ya hay pausa entre cada una y el circuit breaker
    // (pareceCaido()) sigue protegiendo contra una racha de fallos.
    private List<ActuacionJudicialRegistrada> construirObjetivos() {
        List<ActuacionJudicialRegistrada> todas = new ArrayList<>(actuacionRepository.ultimaActuacionConFechaPorCaso());
        todas.addAll(actuacionRepository.conFechaRegistroDesde(
                LocalDate.now().minusDays(DIAS_VENTANA_BUSQUEDA).atStartOfDay()));
        todas.sort(Comparator
                .comparing((ActuacionJudicialRegistrada a) -> a.getCaso().getId())
                .thenComparing(a -> fechaDeBoletin(a), Comparator.nullsLast(Comparator.reverseOrder())));
        LinkedHashMap<Long, ActuacionJudicialRegistrada> porId = new LinkedHashMap<>();
        for (ActuacionJudicialRegistrada a : todas) {
            LocalDate fecha = fechaDeBoletin(a);
            // Sin radicado CPNU valido el cliente ni siquiera consulta la red, pero igual se
            // pagaba la pausa de 20 s: se descartan de entrada.
            if (fecha == null || !RadicadoUtil.esFormatoCpnuValido(a.getCaso().getRadicadoId())) {
                continue;
            }
            porId.putIfAbsent(a.getId(), a);
        }
        return new ArrayList<>(porId.values());
    }

    private static LocalDate fechaDeBoletin(ActuacionJudicialRegistrada a) {
        if (a.getFechaRegistro() != null) {
            return a.getFechaRegistro().toLocalDate();
        }
        return a.getFechaActuacion() != null ? a.getFechaActuacion().toLocalDate() : null;
    }

    private void sincronizarNuevosBoletinesInterno() {
        List<ActuacionJudicialRegistrada> actuaciones = construirObjetivos();
        progresoFase = "PASADA";
        reintentosTotal.set(0);
        reintentosHechos.set(0);
        recuperados.set(0);
        if (actuaciones.isEmpty()) {
            log.info("Cache de boletines judiciales: 0 actuación(es) revisada(s) de 0, 0 boletín(es) nuevo(s) cacheado(s).");
            progresoFase = "EN_ESPERA";
            recuperarPendientes();
            return;
        }
        // Rota la lista para que esta corrida empiece donde se quedó la anterior (ver
        // VigilanciaCursorBoletines arriba) -- nunca siempre desde el índice 0. "% size" es
        // seguro aunque la cantidad de actuaciones haya cambiado entre ciclos (casos nuevos,
        // casos eliminados).
        progresoTotal.set(actuaciones.size());
        progresoRevisadas.set(0);
        progresoNuevos.set(0);
        progresoInicio = LocalDateTime.now();
        progresoFin = null;
        VigilanciaCursorBoletines cursor = cargarCursor();
        int inicio = Math.floorMod(cursor.getPosicion(), actuaciones.size());
        if (inicio > 0) {
            List<ActuacionJudicialRegistrada> rotada = new ArrayList<>(actuaciones.subList(inicio, actuaciones.size()));
            rotada.addAll(actuaciones.subList(0, inicio));
            actuaciones = rotada;
        }

        int nuevos = 0;
        int revisadas = 0;
        for (int i = 0; i < actuaciones.size(); i++) {
            // Freno de seguridad real, encontrado en vivo al volver este ciclo continuo
            // (pedido del usuario: "el sistema nunca debe parar de buscar actualizaciones"):
            // si Publicaciones Procesales lleva varias consultas seguidas sin responder
            // (ver PublicacionesProcesalesClient.pareceCaido()), seguir golpeándolo durante
            // la hora completa que tardaría terminar la lista no ayuda a nadie -- se corta
            // aquí, y el siguiente ciclo nocturno retoma EXACTAMENTE donde se quedó este
            // (ver cursor.setPosicion() al final), nunca otra vez desde el principio.
            if (publicacionesProcesalesClient.pareceCaido() && !esperarRecuperacionPortal()) {
                log.warn("Cache de boletines judiciales: Publicaciones Procesales no se recuperó tras esperar "
                                + "{} min -- se corta esta corrida en {}/{}, se retoma en el próximo ciclo.",
                        MAX_ESPERAS_PORTAL * ESPERA_PORTAL_MS / 60_000L, i, actuaciones.size());
                break;
            }
            ActuacionJudicialRegistrada actuacion = actuaciones.get(i);
            try {
                ResultadoActuacion resultado = procesarActuacion(actuacion.getCaso(), actuacion);
                nuevos += resultado.nuevos();
                if (resultado.busquedaIncompleta()) {
                    registrarPendienteBusqueda(actuacion, "Consulta incompleta al portal");
                }
            } catch (Exception ex) {
                log.warn("Fallo inesperado cacheando boletines de la actuación {}: {}",
                        actuacion.getId(), ex.getMessage());
                registrarPendienteBusqueda(actuacion, "Fallo inesperado: " + ex.getMessage());
            }
            revisadas++;
            progresoRevisadas.set(revisadas);
            progresoNuevos.set(nuevos);
            if (i < actuaciones.size() - 1) {
                pausar(pausaEntreConsultasMs);
            }
        }
        cursor.setPosicion(cursor.getPosicion() + revisadas);
        cursorRepository.save(cursor);
        log.info("Cache de boletines judiciales: {} actuación(es) revisada(s) de {}, {} boletín(es) nuevo(s) cacheado(s), "
                        + "{} pendiente(s) de reintento.",
                revisadas, actuaciones.size(), nuevos, contarPendientes());

        // Segunda etapa: volver SOLO sobre lo que fallo (busquedas incompletas y PDFs ya
        // localizados que no se pudieron bajar), sin repetir lo que ya salio bien.
        recuperarPendientes();
        progresoFase = "EN_ESPERA";
        progresoFin = LocalDateTime.now();
        log.info("Cache de boletines judiciales: ciclo terminado -- {} boletín(es) nuevo(s) en total, "
                        + "{} recuperado(s) en la segunda etapa, {} aún pendiente(s).",
                progresoNuevos.get(), recuperados.get(), contarPendientes());
    }

    private record ResultadoActuacion(int nuevos, boolean busquedaIncompleta) {
    }

    // Cuando el freno de seguridad salta (portal sin responder), en vez de rendirse espera y
    // sondea cada ESPERA_PORTAL_MS, hasta MAX_ESPERAS_PORTAL veces (60 min en total). Si el
    // portal vuelve, el recorrido sigue EXACTAMENTE donde estaba; si no vuelve, se corta y
    // el cursor/cola conservan todo para el siguiente ciclo.
    private static final long ESPERA_PORTAL_MS = 600_000L;
    private static final int MAX_ESPERAS_PORTAL = 6;

    private boolean esperarRecuperacionPortal() {
        String faseAnterior = progresoFase;
        progresoFase = "ESPERANDO_PORTAL";
        try {
            for (int intento = 1; intento <= MAX_ESPERAS_PORTAL; intento++) {
                log.warn("Cache de boletines judiciales: el portal no responde, espero {} min y vuelvo a probar "
                        + "(intento {}/{}).", ESPERA_PORTAL_MS / 60_000L, intento, MAX_ESPERAS_PORTAL);
                pausar(ESPERA_PORTAL_MS);
                if (publicacionesProcesalesClient.sondearPortal()) {
                    log.info("Cache de boletines judiciales: el portal volvió a responder, se reanuda el recorrido.");
                    return true;
                }
            }
            return false;
        } finally {
            progresoFase = faseAnterior;
        }
    }

    // Reintenta lo que quedo en la cola de pendientes, en rondas con espera entre ellas. Nunca
    // lanza: cada pendiente se maneja de forma aislada y, si el portal sigue caido, la cola se
    // conserva para el siguiente ciclo (no se pierde nada).
    private void recuperarPendientes() {
        for (int ronda = 1; ronda <= MAX_RONDAS_RECUPERACION; ronda++) {
            List<BoletinPendiente> pendientes;
            try {
                pendientes = pendienteRepository.listarActivos();
            } catch (Exception ex) {
                log.warn("Cache de boletines judiciales: no se pudo leer la cola de pendientes: {}", ex.getMessage());
                return;
            }
            if (pendientes.isEmpty()) {
                return;
            }
            if (publicacionesProcesalesClient.pareceCaido() && !esperarRecuperacionPortal()) {
                log.warn("Cache de boletines judiciales: el portal sigue sin responder, {} pendiente(s) "
                        + "se conservan para el próximo ciclo.", pendientes.size());
                return;
            }
            progresoFase = "REINTENTOS";
            reintentosTotal.set(pendientes.size());
            reintentosHechos.set(0);
            log.info("Cache de boletines judiciales: ronda de reintento {}/{} sobre {} pendiente(s).",
                    ronda, MAX_RONDAS_RECUPERACION, pendientes.size());
            int resueltos = 0;
            for (BoletinPendiente pendiente : pendientes) {
                if (publicacionesProcesalesClient.pareceCaido() && !esperarRecuperacionPortal()) {
                    break;
                }
                try {
                    if (resolverPendiente(pendiente)) {
                        resueltos++;
                    }
                } catch (Exception ex) {
                    registrarFalloPendiente(pendiente, ex.getMessage());
                }
                reintentosHechos.incrementAndGet();
                pausar(pendiente.getTipo() == BoletinPendiente.Tipo.DESCARGA
                        ? Math.max(1000L, pausaEntreConsultasMs / 4) : pausaEntreConsultasMs);
            }
            log.info("Cache de boletines judiciales: ronda {} resolvió {} de {} pendiente(s).",
                    ronda, resueltos, pendientes.size());
            if (ronda < MAX_RONDAS_RECUPERACION && pendienteRepository.countByAgotadoFalse() > 0) {
                pausar(ESPERA_ENTRE_RONDAS_MS);
            }
        }
    }

    // true si el pendiente quedo resuelto (y se borra de la cola).
    private boolean resolverPendiente(BoletinPendiente pendiente) {
        Caso caso = pendiente.getCaso();
        if (pendiente.getTipo() == BoletinPendiente.Tipo.DESCARGA) {
            String url = pendiente.getUrlOrigen();
            if (boletinRepository.existsByCaso_IdAndUrlOrigen(caso.getId(), url)) {
                pendienteRepository.delete(pendiente);
                return true;
            }
            try {
                byte[] bytes = publicacionesProcesalesClient.descargarDocumentoOExcepcion(
                        url, PublicacionesProcesalesClient.INTENTOS_DESCARGA_CICLO);
                guardarBoletin(caso, pendiente.getActuacion(), pendiente.getNombreArchivo(), url,
                        pendiente.isCoincideNombre(), bytes);
                progresoNuevos.incrementAndGet();
                recuperados.incrementAndGet();
                pendienteRepository.delete(pendiente);
                return true;
            } catch (DescargaDocumentoFallidaException ex) {
                if (!ex.isReintentable()) {
                    // El documento ya no existe o la URL no sirve: no tiene sentido insistir.
                    log.warn("Cache de boletines judiciales: se descarta el pendiente {} ({}).", url, ex.getMessage());
                    pendienteRepository.delete(pendiente);
                    return false;
                }
                registrarFalloPendiente(pendiente, ex.getMessage());
                return false;
            }
        }
        ActuacionJudicialRegistrada actuacion = pendiente.getActuacion();
        if (actuacion == null) {
            pendienteRepository.delete(pendiente);
            return false;
        }
        ResultadoActuacion resultado = procesarActuacion(caso, actuacion);
        if (resultado.nuevos() > 0) {
            progresoNuevos.addAndGet(resultado.nuevos());
            recuperados.addAndGet(resultado.nuevos());
        }
        if (resultado.busquedaIncompleta()) {
            registrarFalloPendiente(pendiente, "Consulta incompleta al portal");
            return false;
        }
        pendienteRepository.delete(pendiente);
        return true;
    }

    private void registrarFalloPendiente(BoletinPendiente pendiente, String error) {
        pendiente.setIntentos(pendiente.getIntentos() + 1);
        pendiente.setUltimoError(error != null && error.length() > 500 ? error.substring(0, 500) : error);
        pendiente.setFechaUltimoIntento(LocalDateTime.now());
        if (pendiente.getIntentos() >= MAX_INTENTOS_PENDIENTE) {
            pendiente.setAgotado(true);
            log.warn("Cache de boletines judiciales: pendiente {} ({}) agotó {} intentos, se deja de reintentar. "
                            + "Último error: {}",
                    pendiente.getId(), pendiente.getTipo(), MAX_INTENTOS_PENDIENTE, error);
        }
        try {
            pendienteRepository.save(pendiente);
        } catch (Exception ex) {
            log.warn("Cache de boletines judiciales: no se pudo actualizar el pendiente {}: {}",
                    pendiente.getId(), ex.getMessage());
        }
    }

    private void registrarPendienteBusqueda(ActuacionJudicialRegistrada actuacion, String error) {
        try {
            BoletinPendiente pendiente = pendienteRepository
                    .findByTipoAndActuacion_Id(BoletinPendiente.Tipo.BUSQUEDA, actuacion.getId())
                    .orElseGet(BoletinPendiente::new);
            pendiente.setCaso(actuacion.getCaso());
            pendiente.setActuacion(actuacion);
            pendiente.setTipo(BoletinPendiente.Tipo.BUSQUEDA);
            pendiente.setUltimoError(error);
            pendiente.setFechaUltimoIntento(LocalDateTime.now());
            pendienteRepository.save(pendiente);
        } catch (Exception ex) {
            log.warn("Cache de boletines judiciales: no se pudo registrar la búsqueda pendiente de la actuación {}: {}",
                    actuacion.getId(), ex.getMessage());
        }
    }

    private void registrarPendienteDescarga(Caso caso, ActuacionJudicialRegistrada actuacion,
                                            DocumentoProvidenciaCandidato candidato, String error) {
        try {
            BoletinPendiente pendiente = pendienteRepository
                    .findByTipoAndCaso_IdAndUrlOrigen(BoletinPendiente.Tipo.DESCARGA, caso.getId(), candidato.urlDescarga())
                    .orElseGet(BoletinPendiente::new);
            pendiente.setCaso(caso);
            pendiente.setActuacion(actuacion);
            pendiente.setTipo(BoletinPendiente.Tipo.DESCARGA);
            pendiente.setUrlOrigen(candidato.urlDescarga());
            pendiente.setNombreArchivo(candidato.nombreArchivo());
            pendiente.setCoincideNombre(candidato.coincideConNombreActuacion());
            pendiente.setUltimoError(error != null && error.length() > 500 ? error.substring(0, 500) : error);
            pendiente.setFechaUltimoIntento(LocalDateTime.now());
            pendienteRepository.save(pendiente);
        } catch (Exception ex) {
            log.warn("Cache de boletines judiciales: no se pudo registrar la descarga pendiente {}: {}",
                    candidato.urlDescarga(), ex.getMessage());
        }
    }

    // Bug real reportado por el usuario: "en ninguno de los casos con boletin disponible
    // sale el boton azul". Causa -- CasoAdminController.documentosBoletin() (la búsqueda EN
    // VIVO que usa el modal de Seguimiento, buscarDocumentosBoletin en el frontend) nunca
    // escribía en esta caché: solo sincronizarNuevosBoletines() lo hacía, y ese método solo
    // corre una vez al día (7am, ver BoletinJudicialScheduler) -- si ese ciclo no alcanza a
    // correr (ej. el backend se reinició justo a esa hora, como pasó hoy en desarrollo), el
    // punto azul del listado se queda apagado para TODOS los casos aunque el admin ya haya
    // visto boletines reales disponibles abriendo el modal de cada uno. Esto cierra ese hueco:
    // cualquier candidato que ya se le mostró al admin en una consulta manual también se cachea
    // aquí mismo, así el punto azul refleja de inmediato lo que el admin ya vio, sin depender
    // únicamente del ciclo de las 7am. actuacion=null a propósito: una consulta manual no
    // necesariamente trae la entidad ActuacionJudicialRegistrada, y la columna lo permite (ver
    // BoletinJudicialCache.actuacion, FK ON DELETE SET NULL).
    public void cachearDesdeConsultaManual(Caso caso, List<DocumentoProvidenciaCandidato> candidatos) {
        for (DocumentoProvidenciaCandidato candidato : candidatos) {
            if (boletinRepository.existsByCaso_IdAndUrlOrigen(caso.getId(), candidato.urlDescarga())) {
                continue;
            }
            cachear(caso, null, candidato);
        }
    }

    // Busca los boletines de UNA actuacion y los guarda. Todo PDF localizado se descarga con
    // reintentos; si aun asi falla, queda en la cola de pendientes (nunca se pierde). El
    // resultado indica si la busqueda quedo incompleta (el portal fallo) para que el llamador
    // la reintente.
    //
    // Canal EXACTO primero: la propia CPNU resuelve el documento ligado al idRegActuacion real
    // (RamaJudicialCpnuClient.listarDocumentosDeActuacion -- sin heuristica de texto), igual
    // que el canal "Unificada en tiempo real" que LUPA e IUS ya ofrecen por separado del
    // boletin por despacho. Esto es lo que arregla de raiz el bug reportado por el usuario
    // (caso 11001418907820250083400 trayendo boletines de OTRO proceso del mismo despacho):
    // el boletin heuristico de Publicaciones Procesales agrupa TODOS los procesos de un
    // despacho en un mismo PDF y solo empareja por año+consecutivo del radicado dentro del
    // texto, mientras que este canal usa el id real de la actuacion. Solo cae al heuristico
    // (como respaldo, nunca al reves) cuando el CPNU no expone el documento directo para ese
    // despacho en particular.
    private ResultadoActuacion procesarActuacion(Caso caso, ActuacionJudicialRegistrada actuacion) {
        if (caso.getRadicadoId() == null || caso.getRadicadoId().isBlank()) {
            return new ResultadoActuacion(0, false);
        }
        if (actuacion.isConDocumentos() && !cpnuClient.pareceCaido()) {
            List<DocumentoProvidenciaCandidato> exactos = cpnuClient.listarDocumentosDeActuacion(
                    actuacion.getIdActuacionExterna(), actuacion.getNombreActuacion());
            if (!exactos.isEmpty()) {
                return descargarYGuardarCandidatos(caso, actuacion, exactos, false);
            }
        }
        PublicacionesProcesalesClient.ResultadoBusqueda busqueda = publicacionesProcesalesClient.buscarDocumentosConEstado(
                caso.getRadicadoId(), fechaDeBoletin(actuacion), actuacion.getNombreActuacion());
        ResultadoActuacion resultado = descargarYGuardarCandidatos(caso, actuacion, busqueda.candidatos(), busqueda.incompleta());

        // Pedido explícito del usuario (2026-11-04, replicando el flujo real de LUPA
        // Jurídica): si NI el canal exacto de la CPNU NI el heurístico de Publicaciones
        // Procesales encontraron nada para una actuación que la propia Rama Judicial marcó
        // con conDocumentos=true, se le solicita el documento por correo directamente al
        // despacho (ver CorreoDespachoJudicialService). "incompleta" (el portal falló a
        // mitad de la búsqueda) no cuenta como "no existe": en ese caso se reintentará en un
        // ciclo posterior, nunca se le escribe al despacho por una falla transitoria propia.
        //
        // Bug real encontrado (2026-11-05): esta misma actuación vuelve a pasar por aquí en
        // CADA ciclo (ver construirObjetivos -- la última actuación de cada caso SIEMPRE
        // entra, y cualquier actuación reciente reentra mientras siga en la ventana de días).
        // Si el documento YA se encontró y cacheó en un ciclo ANTERIOR, este ciclo lo
        // descarta como duplicado (existsByCaso_IdAndUrlOrigen) y resultado.nuevos() da 0 --
        // sin el chequeo de abajo, eso se interpretaba como "nunca se encontró nada" y se le
        // escribía igual al despacho pidiendo un documento que el sistema YA tiene guardado.
        if (actuacion.isConDocumentos() && resultado.nuevos() == 0 && !busqueda.incompleta()
                && !boletinRepository.existsByActuacion_IdAndCoincideNombreActuacionTrue(actuacion.getId())) {
            correoDespachoJudicialService.intentarSolicitarAlDespacho(caso, actuacion);
        }
        return resultado;
    }

    // Descarga y cachea cada candidato nuevo (ver guardarBoletin) -- mismo flujo tanto para el
    // canal exacto de CPNU como para el heuristico de boletines; la unica diferencia entre
    // ambos es de donde vinieron los candidatos. descargarDocumentoOExcepcion ya valida y
    // permite tanto el host de Publicaciones Procesales como el de CPNU (consultaprocesos.
    // ramajudicial.gov.co), asi que no hace falta un segundo mecanismo de descarga.
    private ResultadoActuacion descargarYGuardarCandidatos(Caso caso, ActuacionJudicialRegistrada actuacion,
                                                            List<DocumentoProvidenciaCandidato> candidatos,
                                                            boolean incompleta) {
        int nuevos = 0;
        for (DocumentoProvidenciaCandidato candidato : candidatos) {
            // Bug real encontrado (2026-11-05, caso 11001418907820250083400): un candidato del
            // heurístico de Publicaciones Procesales con coincideConNombreActuacion=false ya
            // significa "el propio emparejador de texto NO confía en esto" -- y aun así se
            // descargaba y se guardaba igual, mostrándose en el panel como si fuera un
            // documento real del caso. Así es como un solo boletín completo de un despacho
            // (145 PDFs de OTRO proceso, mismo juzgado) terminó atribuido entero a este caso.
            // El canal exacto de CPNU siempre viene con coincideConNombreActuacion=true (ver
            // listarDocumentosDeActuacion), así que esto nunca afecta ese canal -- solo
            // descarta candidatos heurísticos de baja confianza, que de todas formas quedan
            // excluidos de los reportes a clientes (ver BoletinJudicialDigestService.
            // enviarDigestClientes, AndCoincideNombreActuacionTrue).
            if (!candidato.coincideConNombreActuacion()) {
                continue;
            }
            if (boletinRepository.existsByCaso_IdAndUrlOrigen(caso.getId(), candidato.urlDescarga())) {
                continue;
            }
            try {
                byte[] bytes = publicacionesProcesalesClient.descargarDocumentoOExcepcion(
                        candidato.urlDescarga(), PublicacionesProcesalesClient.INTENTOS_DESCARGA_CICLO);
                guardarBoletin(caso, actuacion, candidato.nombreArchivo(), candidato.urlDescarga(),
                        candidato.coincideConNombreActuacion(), bytes);
                pendienteRepository.borrarDescarga(caso.getId(), candidato.urlDescarga());
                nuevos++;
            } catch (DescargaDocumentoFallidaException ex) {
                if (ex.isReintentable()) {
                    registrarPendienteDescarga(caso, actuacion, candidato, ex.getMessage());
                }
            }
        }
        return new ResultadoActuacion(nuevos, incompleta);
    }

    private void guardarBoletin(Caso caso, ActuacionJudicialRegistrada actuacion, String nombreArchivo,
                                String urlOrigen, boolean coincideNombre, byte[] bytes) {
        BoletinJudicialCache nuevo = new BoletinJudicialCache();
        nuevo.setCaso(caso);
        nuevo.setActuacion(actuacion);
        nuevo.setNombreArchivo(nombreArchivo);
        nuevo.setUrlOrigen(urlOrigen);
        nuevo.setContenidoBytes(bytes);
        nuevo.setHashSha256(sha256(bytes));
        nuevo.setTokenPublico(java.util.UUID.randomUUID().toString());
        nuevo.setCoincideNombreActuacion(coincideNombre);
        LocalDateTime ahora = LocalDateTime.now();
        nuevo.setFechaDescarga(ahora);
        nuevo.setFechaExpiracion(ahora.plusDays(DIAS_VENCIMIENTO_CACHE));
        boletinRepository.save(nuevo);
    }

    // Sin @Transactional a propósito: se llama por self-invocation desde procesarActuacion()
    // en esta misma clase, donde Spring AOP NUNCA intercepta la llamada (bypasea el proxy
    // por completo) -- la anotación quedaría silenciosamente ignorada. No hace falta de
    // todas formas: el único cambio de estado es boletinRepository.save(), que Spring Data
    // JPA ya envuelve en su propia transacción por cada llamada.
    private boolean cachear(Caso caso, ActuacionJudicialRegistrada actuacion, DocumentoProvidenciaCandidato candidato) {
        return publicacionesProcesalesClient.descargarDocumento(candidato.urlDescarga())
                .map(bytes -> {
                    guardarBoletin(caso, actuacion, candidato.nombreArchivo(), candidato.urlDescarga(),
                            candidato.coincideConNombreActuacion(), bytes);
                    return true;
                })
                .orElse(false);
    }

    // BoletinJudicialScheduler la llama diario, aparte de sincronizarNuevosBoletines() --
    // "guardarlos... por un total de 30 dias... ya el 30 se eliminaran" (pedido explícito del
    // usuario), sin excepción ni prórroga.
    @Transactional
    public void purgarExpirados() {
        List<BoletinJudicialCache> expirados = boletinRepository.findByFechaExpiracionBefore(LocalDateTime.now());
        if (!expirados.isEmpty()) {
            boletinRepository.deleteAll(expirados);
            log.info("Cache de boletines judiciales: {} boletín(es) vencido(s) borrado(s) ({} días cumplidos).",
                    expirados.size(), DIAS_VENCIMIENTO_CACHE);
        }
    }

    private static String sha256(byte[] datos) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(datos);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }

    private void pausar(long milisegundos) {
        try {
            Thread.sleep(milisegundos);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
