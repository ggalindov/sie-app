package sie.siejuridicos.vigilancia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sie.siejuridicos.caso.Caso;
import sie.siejuridicos.caso.CasoRepository;
import sie.siejuridicos.common.exception.RecursoNoEncontradoException;
import sie.siejuridicos.tarea.Tarea;
import sie.siejuridicos.tarea.TareaService;
import sie.siejuridicos.usuario.UsuarioInterno;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

// Gap real detectado en la auditoria del plan de desarrollo: el termino procesal
// (InferenciaTerminoProcesal) se calculaba al vuelo solo para mostrarse como insignia
// dentro del modal de Seguimiento -- si el abogado no entraba a revisarlo, no habia ninguna
// alerta activa en otro lado del panel. Este servicio lo persiste (ver migracion V60,
// TerminoProcesal) y, si el caso tiene abogado asignado (ver migracion V61,
// Caso.abogadoAsignado), le crea de inmediato una tarea de prioridad ALTA -- asi aparece
// en /admin/tareas y con semaforo en /admin/calendario sin que nadie tenga que entrar a
// revisar el seguimiento judicial de ese caso puntual.
//
// Llamado desde SeguimientoJudicialService.sincronizarCaso() justo despues de guardar (o
// completar los campos de) cada actuacion -- NUNCA directamente por un controlador, este
// servicio no expone ningun endpoint propio.
@Service
public class TerminoProcesalService {

    private static final Logger log = LoggerFactory.getLogger(TerminoProcesalService.class);

    private final TerminoProcesalRepository terminoProcesalRepository;
    private final CasoRepository casoRepository;
    private final ActuacionJudicialRegistradaRepository actuacionRepository;
    private final TareaService tareaService;

    public TerminoProcesalService(TerminoProcesalRepository terminoProcesalRepository,
                                   CasoRepository casoRepository,
                                   ActuacionJudicialRegistradaRepository actuacionRepository,
                                   TareaService tareaService) {
        this.terminoProcesalRepository = terminoProcesalRepository;
        this.casoRepository = casoRepository;
        this.actuacionRepository = actuacionRepository;
        this.tareaService = tareaService;
    }

    // Bug real encontrado probando en vivo: recibia el Caso/ActuacionJudicialRegistrada ya
    // cargados desde SeguimientoJudicialService, que corre DELIBERADAMENTE sin
    // @Transactional (ver el comentario de esa clase) -- para cuando este metodo accedia a
    // actuacion.getCaso() o caso.getAbogadoAsignado() (relaciones LAZY), la transaccion
    // original ya estaba cerrada y ambos objetos quedaban "detached", reventando con
    // LazyInitializationException ("no session"). La solucion: recibir solo el ID del caso
    // (un valor plano, nunca lazy) y recargar el Caso de cero DENTRO de la propia
    // transaccion de este metodo (ver CasoRepository.buscarConClienteYAbogadoPorId, ya trae
    // abogadoAsignado con LEFT JOIN FETCH). Los campos simples de `actuacion` (nombre, fechas)
    // SI son seguros de leer tal cual lleguen -- son columnas propias, no relaciones.
    //
    // Idempotente por actuacion (ver TerminoProcesalRepository.existsByActuacion_Id): se
    // puede llamar tanto para una actuacion recien guardada como para una ya existente
    // (backfill, ver SeguimientoJudicialService.completarCamposCapturadosDespues) sin
    // riesgo de duplicar el termino ni la tarea si ya se habia procesado antes.
    @Transactional
    public void procesarSiAplica(Long casoId, ActuacionJudicialRegistrada actuacion) {
        if (actuacion.getId() == null || terminoProcesalRepository.existsByActuacion_Id(actuacion.getId())) {
            return;
        }

        boolean oficial = actuacion.getFechaInicialTermino() != null && actuacion.getFechaFinalTermino() != null;
        LocalDate fechaRegistroSolo = actuacion.getFechaRegistro() != null ? actuacion.getFechaRegistro().toLocalDate() : null;
        Optional<InferenciaTerminoProcesal.TerminoInferido> termino = oficial
                ? Optional.of(InferenciaTerminoProcesal.desdeFechaOficial(
                        actuacion.getFechaInicialTermino(), actuacion.getFechaFinalTermino()))
                : InferenciaTerminoProcesal.inferir(actuacion.getNombreActuacion(), fechaRegistroSolo);
        if (termino.isEmpty()) {
            return;
        }

        Caso caso = casoRepository.buscarConClienteYAbogadoPorId(casoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Caso no encontrado: " + casoId));

        TerminoProcesal nuevo = new TerminoProcesal();
        nuevo.setCaso(caso);
        // getReferenceById en vez de la `actuacion` recibida por parámetro: un proxy que
        // Hibernate resuelve dentro de ESTA transacción si hace falta, nunca un objeto
        // detached de una transacción ajena ya cerrada.
        nuevo.setActuacion(actuacionRepository.getReferenceById(actuacion.getId()));
        nuevo.setDescripcion(termino.get().descripcion());
        nuevo.setDiasHabiles(termino.get().diasHabiles());
        nuevo.setFechaVencimiento(termino.get().fechaVencimiento());
        nuevo.setEsOficial(oficial);

        UsuarioInterno abogado = caso.getAbogadoAsignado();
        if (abogado != null) {
            Tarea tarea = tareaService.crearAutomatica(
                    caso,
                    abogado,
                    termino.get().descripcion(),
                    "Detectado automáticamente por el seguimiento judicial 24/7 a partir de la actuación "
                            + "\"%s\" (%s). Vence el %s (%d días hábiles)%s."
                                    .formatted(actuacion.getNombreActuacion(), caso.getRadicadoId(),
                                            termino.get().fechaVencimiento(), termino.get().diasHabiles(),
                                            oficial ? ", informado directamente por el despacho" : ", estimado -- confirma leyendo el auto"),
                    termino.get().fechaVencimiento().atTime(LocalTime.of(8, 0)));
            nuevo.setTarea(tarea);
        } else {
            log.warn("Término procesal detectado en el caso {} sin abogado asignado -- no se creó ninguna "
                    + "tarea, el término queda registrado sin alerta activa hasta que se asigne un abogado.",
                    caso.getId());
        }

        terminoProcesalRepository.save(nuevo);
    }

    // Para CasoService.actualizar(): un caso puede tener términos detectados ANTES de que
    // existiera un abogado asignado (ver procesarSiAplica -- este sistema ya llevaba 362
    // casos sincronizados antes de que "abogado asignado" existiera siquiera, ver migración
    // V61). Sin esto, esos términos se quedarían para siempre sin tarea ni alerta activa,
    // porque procesarSiAplica nunca reprocesa una actuación ya registrada (es idempotente a
    // propósito). Se llama cada vez que CasoService.actualizar() le asigna un abogado nuevo
    // a un caso.
    @Transactional
    public void asignarTareasPendientes(Caso caso, UsuarioInterno abogado) {
        List<TerminoProcesal> sinTarea = terminoProcesalRepository.findByCaso_IdAndTareaIsNull(caso.getId());
        for (TerminoProcesal termino : sinTarea) {
            Tarea tarea = tareaService.crearAutomatica(
                    caso,
                    abogado,
                    termino.getDescripcion(),
                    "Término procesal ya detectado por el seguimiento judicial 24/7 (asignado ahora porque "
                            + "este caso no tenía abogado en ese momento). Vence el %s (%d días hábiles)%s."
                                    .formatted(termino.getFechaVencimiento(), termino.getDiasHabiles(),
                                            termino.isEsOficial() ? ", informado directamente por el despacho" : ", estimado -- confirma leyendo el auto"),
                    termino.getFechaVencimiento().atTime(LocalTime.of(8, 0)));
            termino.setTarea(tarea);
            terminoProcesalRepository.save(termino);
        }
    }
}
