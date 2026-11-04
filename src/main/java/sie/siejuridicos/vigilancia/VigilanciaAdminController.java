package sie.siejuridicos.vigilancia;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sie.siejuridicos.caso.Caso;
import sie.siejuridicos.caso.CasoRepository;
import sie.siejuridicos.caso.CasoService;
import sie.siejuridicos.caso.dto.CasoAdminResponse;
import sie.siejuridicos.common.exception.RecursoNoEncontradoException;
import sie.siejuridicos.correo.EmailService;
import sie.siejuridicos.security.UsuarioInternoPrincipal;
import sie.siejuridicos.usuario.RolUsuario;
import sie.siejuridicos.whatsapp.WhatsAppService;
import sie.siejuridicos.vigilancia.dto.ActualizarProcesoCandidatoRequest;
import sie.siejuridicos.vigilancia.dto.ActuacionRegistradaResponse;
import sie.siejuridicos.vigilancia.dto.BoletinJudicialResponse;
import sie.siejuridicos.vigilancia.dto.ControlDiarioProcesalResponse;
import sie.siejuridicos.vigilancia.dto.LineaTiempoJudicialResponse;
import sie.siejuridicos.vigilancia.dto.ProcesoCandidatoResponse;
import sie.siejuridicos.vigilancia.dto.TerminoCalendarioResponse;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Mismo alcance de rol que CasoAdminController (hereda hasAnyRole(ADMIN_GENERAL, ABOGADO) de
// /api/admin/** en SecurityConfig, sin restricción adicional): el seguimiento judicial es
// solo lectura hacia la Rama Judicial y nunca envía nada a ningún cliente, así que no hace
// falta restringirlo más que la gestión normal de casos.
@RestController
@RequestMapping("/api/admin/vigilancia")
@PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ABOGADO')")
public class VigilanciaAdminController {

    private final SeguimientoJudicialService seguimientoJudicialService;
    private final ActuacionJudicialRegistradaRepository actuacionRepository;
    private final CasoRepository casoRepository;
    private final TerminoProcesalRepository terminoProcesalRepository;
    private final BoletinJudicialCacheService boletinJudicialCacheService;
    private final BoletinJudicialCacheRepository boletinJudicialCacheRepository;
    private final BoletinJudicialDigestService boletinJudicialDigestService;
    private final EmailService emailService;
    private final ProcesoCandidatoDetectadoRepository procesoCandidatoDetectadoRepository;
    private final CasoService casoService;
    private final WhatsAppService whatsAppService;
    private final VigilanciaExternaOrquestador orquestador;
    private final PortalSaludRepository portalSaludRepository;

    public VigilanciaAdminController(SeguimientoJudicialService seguimientoJudicialService,
                                      ActuacionJudicialRegistradaRepository actuacionRepository,
                                      CasoRepository casoRepository,
                                      TerminoProcesalRepository terminoProcesalRepository,
                                      BoletinJudicialCacheService boletinJudicialCacheService,
                                      BoletinJudicialCacheRepository boletinJudicialCacheRepository,
                                      BoletinJudicialDigestService boletinJudicialDigestService,
                                      EmailService emailService,
                                      ProcesoCandidatoDetectadoRepository procesoCandidatoDetectadoRepository,
                                      CasoService casoService,
                                      WhatsAppService whatsAppService,
                                      VigilanciaExternaOrquestador orquestador,
                                      PortalSaludRepository portalSaludRepository) {
        this.portalSaludRepository = portalSaludRepository;
        this.seguimientoJudicialService = seguimientoJudicialService;
        this.actuacionRepository = actuacionRepository;
        this.casoRepository = casoRepository;
        this.terminoProcesalRepository = terminoProcesalRepository;
        this.boletinJudicialCacheService = boletinJudicialCacheService;
        this.boletinJudicialCacheRepository = boletinJudicialCacheRepository;
        this.boletinJudicialDigestService = boletinJudicialDigestService;
        this.emailService = emailService;
        this.whatsAppService = whatsAppService;
        this.procesoCandidatoDetectadoRepository = procesoCandidatoDetectadoRepository;
        this.casoService = casoService;
        this.orquestador = orquestador;
    }

    // ================= Detección de procesos nuevos (pedido explícito del usuario:
    // "implementa todas las mejoras identificadas", comparando contra Lupa Jurídica) =========
    // Busca por el NOMBRE de cada cliente en el CPNU (no solo por los radicados ya
    // conocidos) -- nunca crea un Caso automáticamente, solo alimenta una lista de
    // candidatos que un abogado/admin revisa aquí.

    @GetMapping("/procesos-detectados")
    @Transactional(readOnly = true)
    public ResponseEntity<List<ProcesoCandidatoResponse>> listarProcesosDetectados() {
        return ResponseEntity.ok(procesoCandidatoDetectadoRepository.listarPendientes().stream()
                .map(ProcesoCandidatoResponse::desde)
                .toList());
    }

    // Pasa por VigilanciaExternaOrquestador (no directo a deteccionProcesosNuevosService)
    // desde el 2026-10-31 -- ver el comentario completo en VigilanciaExternaOrquestador
    // sobre por qué: disparar esto mientras el seguimiento judicial o los boletines ya
    // están en curso generaba tráfico combinado contra la Rama Judicial que causó un
    // bloqueo real esta misma sesión. El mutex global del orquestador hace que, si cualquier
    // otro de los tres ya está corriendo, esta solicitud simplemente se ignore en vez de
    // competir por el mismo tráfico.
    @PostMapping("/procesos-detectados/buscar")
    public ResponseEntity<Void> buscarProcesosDetectados() {
        orquestador.ejecutarManualAsync();
        return ResponseEntity.noContent().build();
    }

    // Botón "No (es otra persona)" -- el único destino real que llega por este endpoint hoy
    // es DESCARTADO (ver ActualizarProcesoCandidatoRequest): "Crear y adjuntar
    // automáticamente" ya no pasa por aquí, tiene su propio endpoint justo abajo porque hace
    // mucho más que cambiar un estado.
    @PatchMapping("/procesos-detectados/{id}")
    @Transactional
    public ResponseEntity<ProcesoCandidatoResponse> actualizarProcesoDetectado(
            @PathVariable Long id, @RequestBody ActualizarProcesoCandidatoRequest request) {
        ProcesoCandidatoDetectado candidato = procesoCandidatoDetectadoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Candidato no encontrado"));
        candidato.setEstado(request.estado());
        return ResponseEntity.ok(ProcesoCandidatoResponse.desde(candidato));
    }

    // Botón "Crear y adjuntar automáticamente en SIE" (pedido explícito del usuario, ver
    // CasoService.crearDesdeProcesoCandidato): crea el Caso real de una vez, con los datos que
    // el CPNU ya trajo durante la detección, y marca el candidato como YA_ES_CASO -- un solo
    // clic en vez de "créalo tú a mano y después vuelve aquí a marcarlo".
    @PostMapping("/procesos-detectados/{id}/crear-caso")
    public ResponseEntity<CasoAdminResponse> crearCasoDesdeProcesoDetectado(
            @PathVariable Long id, @AuthenticationPrincipal UsuarioInternoPrincipal principal) {
        return ResponseEntity.ok(casoService.crearDesdeProcesoCandidato(id, principal.getUsuario()));
    }

    // Disparo manual del CICLO COMPLETO (seguimiento + boletines + detección de procesos
    // nuevos, ver VigilanciaExternaOrquestador), no solo del seguimiento -- desde el
    // 2026-10-31 ya no devuelve el resumen síncrono (nada en el frontend dependía de él:
    // ningún botón del panel llama a este endpoint todavía) porque el ciclo completo tarda
    // horas y correrlo síncrono dejaría la petición HTTP colgada todo ese tiempo.
    @PostMapping("/sincronizar")
    public ResponseEntity<Void> sincronizar() {
        orquestador.ejecutarManualAsync();
        return ResponseEntity.noContent().build();
    }

    // Bug real reportado por el usuario: "el sistema esta trayendo boletines que no son para
    // cada caso" -- la versión anterior de PublicacionesProcesalesClient cacheó documentos de
    // OTROS procesos del mismo despacho bajo el caso equivocado durante buena parte de esta
    // sesión, antes de la corrección real (ver PublicacionesProcesalesClient.
    // documentosDelBoletin, ahora verifica por año+consecutivo del radicado). Limpia TODO lo
    // ya cacheado con la lógica vieja -- es solo una caché temporal (7→30 días, se vuelve a
    // llenar sola), nunca el historial real de actuaciones, así que borrar todo y dejar que se
    // reconstruya con la lógica corregida es más seguro que tratar de adivinar cuáles filas
    // eran válidas y cuáles no.
    @PostMapping("/boletines/limpiar-cache")
    public ResponseEntity<Long> limpiarCacheBoletines() {
        long total = boletinJudicialCacheRepository.count();
        boletinJudicialCacheRepository.deleteAll();
        return ResponseEntity.ok(total);
    }

    // Pedido explícito del usuario: "busca todos los boletines y cargalos en el sistema" --
    // pasa por VigilanciaExternaOrquestador desde el 2026-10-31 (antes llamaba directo a
    // BoletinJudicialCacheService.sincronizarNuevosBoletinesAsync()), que de paso corre
    // seguimiento judicial y detección de procesos nuevos en la misma pasada si no habían
    // corrido ya -- ver el comentario completo ahí. Responde de inmediato, sin cuerpo -- "ya
    // arrancó", no "ya terminó" (mismo criterio de 204 que el resto de este panel usa para
    // acciones sin contenido que devolver).
    @PostMapping("/boletines/sincronizar")
    public ResponseEntity<Void> sincronizarBoletines() {
        orquestador.ejecutarManualAsync();
        return ResponseEntity.noContent().build();
    }

    // Solo el paso de boletines (sin seguimiento ni detección), mismo mutex global que el
    // ciclo completo -- para verificar boletines sin esperar la hora de seguimiento previa.
    // Vacia la cola de boletines pendientes (busquedas incompletas y PDFs localizados que no
    // se pudieron bajar) sin esperar un ciclo completo.
    @PostMapping("/boletines/reintentar-pendientes")
    public ResponseEntity<Void> reintentarPendientesBoletines() {
        orquestador.reintentarPendientesBoletinesAsync();
        return ResponseEntity.noContent().build();
    }

    // Curva de salud de los portales por hora del dia (hora de Bogota) segun la sonda
    // permanente: total de mediciones, fallos y latencia media. Base para elegir la hora del
    // ciclo automatico. horas = ventana hacia atras (por defecto 72).
    @GetMapping("/portales/salud")
    public ResponseEntity<List<PortalSaludRepository.ResumenHora>> saludPortales(
            @RequestParam(value = "horas", defaultValue = "72") int horas) {
        return ResponseEntity.ok(portalSaludRepository.resumenPorHora(Math.max(1, Math.min(horas, 24 * 60))));
    }

    @GetMapping("/boletines/progreso")
    public ResponseEntity<BoletinJudicialCacheService.ProgresoBoletines> progresoBoletines() {
        return ResponseEntity.ok(boletinJudicialCacheService.progreso());
    }

    @PostMapping("/boletines/sincronizar-solo")
    public ResponseEntity<Void> sincronizarSoloBoletines() {
        orquestador.sincronizarSoloBoletinesAsync();
        return ResponseEntity.noContent().build();
    }

    // Pedido explícito del usuario: "el sistema debe de traer y darle la opcion de todos los
    // casos de ver/descargar todos los boletines que esten disponibles en las ramas o donde
    // esten" -- TODOS los boletines cacheados de TODOS los casos en una sola lista (nueva
    // pantalla /admin/boletines), no uno por uno abriendo cada caso.
    @GetMapping("/boletines")
    @Transactional(readOnly = true)
    public ResponseEntity<List<BoletinJudicialResponse>> listarBoletines() {
        return ResponseEntity.ok(boletinJudicialCacheRepository.listarTodosConCaso().stream()
                .map(BoletinJudicialResponse::desde)
                .toList());
    }

    // Sirve el PDF YA cacheado directamente desde la base de datos -- a propósito NUNCA
    // vuelve a consultar Publicaciones Procesales en vivo para esto (a diferencia de
    // documentoReciente/pdf en CasoAdminController, que sí busca en vivo sobre un candidato
    // recién encontrado): el boletín de esta lista ya se descargó y quedó guardado, así que
    // "ver/descargar" debe ser instantáneo y no depender de que el portal del Estado esté
    // respondiendo en ese momento (pedido explícito del usuario: "optimiza para que sea lo
    // mas rapido").
    @GetMapping("/boletines/{id}/pdf")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> descargarBoletin(@PathVariable Long id) {
        BoletinJudicialCache boletin = boletinJudicialCacheRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Boletín no encontrado"));
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        org.springframework.http.ContentDisposition.inline()
                                .filename(boletin.getNombreArchivo()).build().toString())
                .body(boletin.getContenidoBytes());
    }

    // Línea de tiempo judicial de UN caso: la trazabilidad independiente por caso que pidió
    // el usuario (ver LineaTiempoJudicialResponse) -- no solo la lista de actuaciones, sino
    // si el caso tiene un proceso identificado en la Rama Judicial y cuándo fue la última vez
    // que se revisó, sin depender del resumen agregado de la última corrida completa.
    @GetMapping("/casos/{id}/linea-tiempo")
    public ResponseEntity<LineaTiempoJudicialResponse> lineaTiempo(
            @PathVariable Long id, @AuthenticationPrincipal UsuarioInternoPrincipal principal) {
        Caso caso = casoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Caso no encontrado"));
        List<ActuacionRegistradaResponse> eventos = actuacionRepository.findByCasoOrderByFechaRegistroDesc(caso)
                .stream()
                .map(ActuacionRegistradaResponse::desde)
                .toList();
        // Pedido explícito del usuario: abrir esta línea de tiempo ES la acción de "revisar"
        // el seguimiento de este caso para quien la está pidiendo (ver
        // SeguimientoJudicialService.marcarVisto) -- apaga el botón rojo de "sin revisar" en
        // el listado de Casos para ESTE usuario, nunca para los demás.
        if (principal != null) {
            seguimientoJudicialService.marcarVisto(caso, principal.getUsuario());
        }
        return ResponseEntity.ok(LineaTiempoJudicialResponse.desde(caso, eventos));
    }

    // Pedido explícito del usuario (2026-10-31): "las cargas se estan demorando demasiado
    // cuando pongo abrir seguimiento... que no se demore tanto sin que nos lleguen a
    // bloquear, tambien que el sistema lo guarde en el servidor". Antes, abrir el modal de
    // Seguimiento disparaba una consulta EN VIVO a Publicaciones Procesales POR CADA
    // actuación de la línea de tiempo (podían ser decenas, cada una tardando ~8-11s reales,
    // y si el portal estaba degradado, el timeout completo) -- la misma causa del "se quedó
    // pegado" reportado antes en esta sesión. Este endpoint es SOLO LECTURA de lo que
    // BoletinJudicialCacheService ya dejó guardado en el ciclo nocturno (nunca llama a
    // ningún cliente externo), así que responde en milisegundos sin importar cuántas
    // actuaciones tenga el caso. Agrupado por el id real de la actuación (ver
    // ActuacionRegistradaResponse.id, agregado para esto mismo) -- los boletines cacheados
    // SIN actuación asociada (ver BoletinJudicialCache.actuacion, de una consulta manual
    // antigua) quedan fuera de este mapa a propósito, no hay forma de saber a qué suceso de
    // la línea de tiempo pertenecen.
    @GetMapping("/casos/{id}/boletines-cache")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<Long, List<DocumentoProvidenciaCandidato>>> boletinesCache(@PathVariable Long id) {
        Map<Long, List<DocumentoProvidenciaCandidato>> resultado = boletinJudicialCacheRepository.findByCaso_Id(id)
                .stream()
                .filter(b -> b.getActuacion() != null)
                .collect(Collectors.groupingBy(
                        b -> b.getActuacion().getId(),
                        Collectors.mapping(
                                b -> new DocumentoProvidenciaCandidato(
                                        b.getNombreArchivo(), b.getUrlOrigen(), b.isCoincideNombreActuacion()),
                                Collectors.toList())));
        return ResponseEntity.ok(resultado);
    }

    // Gap real detectado en la auditoria del plan de desarrollo: el termino procesal antes
    // solo se veia dentro del modal de Seguimiento -- esto alimenta el semaforo de
    // /admin/calendario (pedido explicito del usuario). Mismo patron de filtrado por rol
    // que SolicitudAdminController.calendario(): un ABOGADO solo ve los terminos de SUS
    // propios casos asignados, ADMIN_GENERAL ve todos (o filtra por abogadoId si quiere).
    @GetMapping("/terminos/calendario")
    public ResponseEntity<List<TerminoCalendarioResponse>> terminosCalendario(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long abogadoId,
            @AuthenticationPrincipal UsuarioInternoPrincipal principal) {
        Long filtroAbogadoId = principal.getUsuario().getRol() == RolUsuario.ADMIN_GENERAL
                ? abogadoId
                : principal.getId();
        boolean esAdmin = principal.getUsuario().getRol() == RolUsuario.ADMIN_GENERAL;

        List<TerminoProcesal> terminos = terminoProcesalRepository.listarEnRango(desde, hasta).stream()
                .filter(t -> filtroAbogadoId == null
                        || (t.getCaso().getAbogadoAsignado() != null
                                && filtroAbogadoId.equals(t.getCaso().getAbogadoAsignado().getId())))
                .filter(t -> esAdmin || !t.getCaso().isEsPrivadoCifrado())
                .toList();

        List<Long> casoIds = terminos.stream().map(t -> t.getCaso().getId()).distinct().toList();
        Map<Long, BoletinJudicialCache> boletinPorActuacion = new java.util.HashMap<>();
        Map<Long, BoletinJudicialCache> boletinPorCaso = new java.util.HashMap<>();
        if (!casoIds.isEmpty()) {
            List<BoletinJudicialCache> bList = boletinJudicialCacheRepository.findByCaso_IdIn(casoIds);
            for (BoletinJudicialCache b : bList) {
                if (b.getActuacion() != null) {
                    boletinPorActuacion.putIfAbsent(b.getActuacion().getId(), b);
                }
                boletinPorCaso.putIfAbsent(b.getCaso().getId(), b);
            }
        }

        List<TerminoCalendarioResponse> resultado = terminos.stream()
                .map(t -> {
                    BoletinJudicialCache bol = null;
                    if (t.getActuacion() != null) {
                        bol = boletinPorActuacion.get(t.getActuacion().getId());
                    }
                    if (bol == null) {
                        bol = boletinPorCaso.get(t.getCaso().getId());
                    }
                    return TerminoCalendarioResponse.desde(t, bol);
                })
                .toList();
        return ResponseEntity.ok(resultado);
    }

    // Réplica del "Control diario procesal" de LUPA (pedido explícito del usuario, 2026-11-04,
    // tras auditar su panel en vivo: "deja la vigilancia lo mas profesional y unica posible").
    // Mismo filtrado por rol que terminosCalendario() de arriba: un ABOGADO solo ve sus
    // propios casos, ADMIN_GENERAL ve todos o filtra por abogadoId. hasta es EXCLUSIVO (el
    // día completo "hasta" queda incluido sumándole 1 día antes de pasarlo al repositorio) --
    // así el llamador solo necesita pensar en fechas de calendario normales, nunca en
    // medianoche exacta.
    @GetMapping("/control-diario")
    @Transactional(readOnly = true)
    public ResponseEntity<ControlDiarioProcesalResponse> controlDiario(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long abogadoId,
            @AuthenticationPrincipal UsuarioInternoPrincipal principal) {
        Long filtroAbogadoId = principal.getUsuario().getRol() == RolUsuario.ADMIN_GENERAL
                ? abogadoId
                : principal.getId();

        List<ActuacionJudicialRegistrada> actuaciones = actuacionRepository
                .findByFechaRegistroEntre(desde.atStartOfDay(), hasta.plusDays(1).atStartOfDay())
                .stream()
                .filter(a -> filtroAbogadoId == null
                        || (a.getCaso().getAbogadoAsignado() != null
                                && filtroAbogadoId.equals(a.getCaso().getAbogadoAsignado().getId())))
                .toList();

        Map<String, Integer> conteos = new LinkedHashMap<>();
        for (ClasificadorMovimientoJudicial.TipoMovimiento tipo : ClasificadorMovimientoJudicial.ORDEN_CONTEO) {
            conteos.put(tipo.name(), 0);
        }
        List<ControlDiarioProcesalResponse.AudienciaAlerta> alertas = new java.util.ArrayList<>();
        for (ActuacionJudicialRegistrada a : actuaciones) {
            ClasificadorMovimientoJudicial.clasificarTipo(a.getNombreActuacion())
                    .ifPresent(tipo -> conteos.merge(tipo.name(), 1, Integer::sum));
            if (ClasificadorMovimientoJudicial.clasificarAsistenciaAudiencia(a.getNombreActuacion(), a.getAnotacion())
                    .filter(r -> r == ClasificadorMovimientoJudicial.AsistenciaAudiencia.NO_ASISTIO)
                    .isPresent()) {
                Caso caso = a.getCaso();
                alertas.add(new ControlDiarioProcesalResponse.AudienciaAlerta(
                        caso.getId(), nombreMostrado(caso), caso.getRadicadoId(),
                        a.getNombreActuacion(), a.getAnotacion(), a.getFechaRegistro(),
                        caso.getAbogadoAsignado() != null ? caso.getAbogadoAsignado().getNombre() : null));
            }
        }

        int vencimientosTermino = (int) terminoProcesalRepository.listarEnRango(desde, hasta).stream()
                .filter(t -> filtroAbogadoId == null
                        || (t.getCaso().getAbogadoAsignado() != null
                                && filtroAbogadoId.equals(t.getCaso().getAbogadoAsignado().getId())))
                .count();

        return ResponseEntity.ok(new ControlDiarioProcesalResponse(desde, hasta, conteos, vencimientosTermino, alertas));
    }

    // ================= Pruebas de envío (pedido explícito del usuario) =================
    // "no vayas a enviar aun a clientes reales, testea con 3126029742/juagdg12@gmail.com" --
    // estos 3 endpoints NUNCA reciben un destinatario del request: siempre usan el correo/
    // teléfono que ya tiene guardado el propio caso (caso.getCliente()), así que solo pueden
    // llegarle a quien sea que ese caso tenga como contacto -- nunca a una dirección
    // arbitraria. Para probar de verdad sin riesgo, se usó un caso NUEVO (id 463, "PRUEBA
    // INTERNA - NO ES CLIENTE REAL") creado con el contacto de prueba autorizado, con
    // boletines copiados del caso 163 (que sí tuvo movimiento real reciente) -- nunca se tocó
    // ningún caso de un cliente real para esto. A diferencia de los flujos automáticos
    // (enviarReporteSemanal(), BoletinJudicialScheduler), estos ignoran a propósito la
    // ventana de "pendiente" (fecha de último envío, banderas enviadoGerente/Cliente): una
    // prueba manual debe poder repetirse las veces que haga falta.

    // Reporte periódico normal (sin boletín), igual que el automático pero para UN solo caso.
    // @Transactional obligatorio, no cosmético: caso.getCliente() es LAZY (ver Caso), y sin
    // una sesión de Hibernate abierta lanza LazyInitializationException -- el mismo bug real
    // que ya costó tiempo en TerminoProcesalService esta misma sesión.
    @Transactional(readOnly = true)
    @PostMapping("/casos/{id}/pruebas/reporte-periodico")
    public ResponseEntity<Boolean> probarReportePeriodico(@PathVariable Long id) {
        Caso caso = casoDePrueba(id);
        boolean exito = emailService.enviarReporteSemanalCasoSincrono(
                nombreMostrado(caso), caso.getCliente().getCorreo(), caso.getRadicadoId());
        return ResponseEntity.ok(exito);
    }

    // Mismo reporte periódico, pero por WhatsApp -- pedido explícito del usuario: "testea...
    // tanto por correo y whatsapp". Mismo criterio de seguridad que el resto de esta sección:
    // nunca manda al teléfono del request, siempre al que ya tiene guardado el propio caso.
    @Transactional(readOnly = true)
    @PostMapping("/casos/{id}/pruebas/reporte-periodico-whatsapp")
    public ResponseEntity<Boolean> probarReportePeriodicoWhatsapp(@PathVariable Long id) {
        Caso caso = casoDePrueba(id);
        if (caso.getCliente().getTelefono() == null) {
            throw new RecursoNoEncontradoException("Este caso no tiene teléfono -- no se puede probar por WhatsApp.");
        }
        boolean exito = whatsAppService.enviarReporteSemanalCasoSincrono(
                nombreMostrado(caso), caso.getCliente().getTelefono(), caso.getRadicadoId());
        return ResponseEntity.ok(exito);
    }

    // Resuelve UN solo caso contra el CPNU ahora mismo (ver SeguimientoJudicialService.
    // sincronizarCaso), sin esperar el ciclo completo de todos los casos con radicado (del
    // orden de una hora) -- pedido explícito del usuario: "testea... asigna un radicado
    // real", necesario para que el caso de prueba tenga partes/ponente/actuaciones reales
    // antes de poder probar los reportes de verdad.
    @Transactional
    @PostMapping("/casos/{id}/pruebas/sincronizar-uno")
    public ResponseEntity<Boolean> probarSincronizarUnCaso(@PathVariable Long id) {
        Caso caso = casoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Caso no encontrado"));
        // Fuerza una resolución fresca de verdad -- sincronizarCaso() solo vuelve a llamar al
        // CPNU cuando idProcesoCpnu o ponente están en null (para no repetir trabajo en el
        // ciclo normal). Si este caso de prueba ya tenía datos resueltos de un radicado
        // ANTERIOR (ej. se le cambió el radicado a mano desde el panel), esos campos se
        // quedarían pegados al proceso viejo para siempre si no se limpian aquí primero.
        caso.setIdProcesoCpnu(null);
        caso.setPonente(null);
        SeguimientoJudicialService.ResultadoCaso resultado = seguimientoJudicialService.sincronizarCaso(caso);
        return ResponseEntity.ok(resultado.tieneProceso());
    }

    // Mismo reporte, pero con el boletín cacheado más reciente del caso adjunto.
    @Transactional(readOnly = true)
    @PostMapping("/casos/{id}/pruebas/reporte-con-boletin")
    public ResponseEntity<Boolean> probarReporteConBoletin(@PathVariable Long id) {
        Caso caso = casoDePrueba(id);
        BoletinJudicialCache masReciente = boletinJudicialCacheRepository.findByCaso_Id(id).stream()
                .max(Comparator.comparing(BoletinJudicialCache::getFechaDescarga))
                .orElseThrow(() -> new RecursoNoEncontradoException("Este caso no tiene ningún boletín cacheado todavía."));
        EmailService.Adjunto adjunto = new EmailService.Adjunto(masReciente.getNombreArchivo(), masReciente.getContenidoBytes());
        boolean exito = emailService.enviarReporteSemanalCasoConBoletinSincrono(
                nombreMostrado(caso), caso.getCliente().getCorreo(), caso.getRadicadoId(), adjunto);
        return ResponseEntity.ok(exito);
    }

    // Digest de boletines -- "interno" reproduce el formato de gerencia/abogados (tabla con
    // la lista de documentos de TODOS los boletines cacheados del caso); "cliente" reproduce
    // el formato de "envío mensual de todos los boletines" al cliente. "correo" opcional
    // (pedido explícito del usuario al revisar el rediseño del 2026-10-31): antes solo podía
    // probarse contra el correo real ya guardado del cliente del caso -- para ver el formato
    // nuevo sin tocar el correo real de un cliente, se puede mandar a cualquier dirección.
    @Transactional(readOnly = true)
    @PostMapping("/casos/{id}/pruebas/digest-boletin")
    public ResponseEntity<Boolean> probarDigestBoletin(
            @PathVariable Long id, @RequestParam("tipo") String tipo,
            @RequestParam(value = "correo", required = false) String correoOverride) {
        Caso caso = casoDePrueba(id);
        String correo = correoOverride != null && !correoOverride.isBlank() ? correoOverride : caso.getCliente().getCorreo();
        boolean exito = "CLIENTE".equalsIgnoreCase(tipo)
                ? boletinJudicialDigestService.probarDigestCliente(id, correo)
                : boletinJudicialDigestService.probarDigestInterno(id, correo);
        return ResponseEntity.ok(exito);
    }

    private Caso casoDePrueba(Long id) {
        Caso caso = casoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Caso no encontrado"));
        if (caso.getCliente() == null || caso.getCliente().getCorreo() == null || caso.getRadicadoId() == null) {
            throw new RecursoNoEncontradoException("Este caso no tiene correo o radicado -- no se puede probar.");
        }
        return caso;
    }

    @PostMapping("/reporte-diario/enviar")
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<Map<String, Object>> enviarReporteDiarioManual() {
        boletinJudicialDigestService.enviarDigestGerente();
        boletinJudicialDigestService.enviarDigestAbogados();
        return ResponseEntity.ok(Map.of("mensaje", "Reporte diario enviado exitosamente a gerencia y abogados."));
    }

    @PostMapping("/reporte-diario/probar")
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<Map<String, Object>> probarReporteDiario(@RequestParam String correo) {
        boolean enviado = boletinJudicialDigestService.probarReporteDiario(correo);
        return ResponseEntity.ok(Map.of("enviado", enviado, "mensaje", enviado ? "Reporte de prueba enviado a " + correo : "Sin registros para probar."));
    }

    // Mismo reporte de prueba, pero con el asunto/saludo de ABOGADO en vez de gerencia --
    // pedido explícito del usuario para visualizar los 3 formatos (gerencia/abogado/cliente)
    // antes de confiar en el envío real.
    @PostMapping("/reporte-diario/probar-abogado")
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<Map<String, Object>> probarReporteDiarioAbogado(
            @RequestParam String correo,
            @RequestParam(defaultValue = "Abogado de Prueba") String nombre) {
        boolean enviado = boletinJudicialDigestService.probarReporteDiarioAbogado(correo, nombre);
        return ResponseEntity.ok(Map.of("enviado", enviado, "mensaje", enviado ? "Reporte de prueba (abogado) enviado a " + correo : "Sin registros para probar."));
    }

    private static String nombreMostrado(Caso caso) {
        return caso.getNombreEnHoja() != null ? caso.getNombreEnHoja() : caso.getCliente().getNombre();
    }
}
