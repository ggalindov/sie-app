package sie.siejuridicos.tarea;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sie.siejuridicos.caso.Caso;
import sie.siejuridicos.caso.CasoRepository;
import sie.siejuridicos.registro.RegistroSistemaService;
import sie.siejuridicos.registro.TipoRegistroSistema;
import sie.siejuridicos.tarea.dto.ActualizarTareaRequest;
import sie.siejuridicos.tarea.dto.CrearTareaRequest;
import sie.siejuridicos.tarea.dto.TareaResponse;
import sie.siejuridicos.usuario.RolUsuario;
import sie.siejuridicos.usuario.UsuarioInterno;
import sie.siejuridicos.usuario.UsuarioInternoRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// Registro de tareas por caso con responsable real (pedido explícito del usuario, ver
// migración V45). Pedido explícito también: "esta habilidad la podra tener tanto admin como
// cualquiera de los abogados" -- ADMIN_GENERAL y ABOGADO pueden crear, reasignar, editar y
// eliminar CUALQUIER tarea de CUALQUIER caso, sin restricción de "solo lo mío" (hereda el
// permiso de clase en TareaController, igual que Casos). La única diferencia por rol está en
// listarPendientes(): un ABOGADO ve solo sus propias tareas pendientes, ADMIN_GENERAL ve todas.
@Service
public class TareaService {

    private final TareaRepository tareaRepository;
    private final CasoRepository casoRepository;
    private final UsuarioInternoRepository usuarioInternoRepository;
    private final RegistroSistemaService registroSistemaService;

    public TareaService(TareaRepository tareaRepository,
                         CasoRepository casoRepository,
                         UsuarioInternoRepository usuarioInternoRepository,
                         RegistroSistemaService registroSistemaService) {
        this.tareaRepository = tareaRepository;
        this.casoRepository = casoRepository;
        this.usuarioInternoRepository = usuarioInternoRepository;
        this.registroSistemaService = registroSistemaService;
    }

    @Transactional(readOnly = true)
    public List<TareaResponse> listarPorCaso(Long casoId) {
        return tareaRepository.findByCasoIdOrderByCompletadaAscFechaVencimientoAsc(casoId).stream()
                .map(TareaResponse::desde)
                .toList();
    }

    // Página "Tareas" del panel: un ABOGADO ve únicamente lo que tiene asignado, ADMIN_GENERAL
    // ve el total de la firma -- así el admin puede detectar de un vistazo qué quedó sin
    // responsable atendiendo, mientras cada abogado se concentra solo en lo suyo.
    @Transactional(readOnly = true)
    public List<TareaResponse> listarPendientes(UsuarioInterno actor) {
        List<Tarea> tareas = actor.getRol() == RolUsuario.ADMIN_GENERAL
                ? tareaRepository.findByCompletadaFalseOrderByFechaVencimientoAsc()
                : tareaRepository.findByUsuarioAsignadoIdAndCompletadaFalseOrderByFechaVencimientoAsc(actor.getId());
        return tareas.stream().map(TareaResponse::desde).toList();
    }

    // Conteo bulk de tareas pendientes por caso (un solo query, no uno por caso -- mismo
    // criterio que ClienteCobroRepository.findByActivoTrueAndClienteCrmIdIsNotNull): alimenta
    // el badge "N tareas pendientes" en el listado de Casos sin N+1.
    @Transactional(readOnly = true)
    public Map<Long, Long> contarPendientesPorCaso() {
        return tareaRepository.contarPendientesPorCaso().stream()
                .collect(Collectors.toMap(fila -> (Long) fila[0], fila -> (Long) fila[1]));
    }

    @Transactional
    public TareaResponse crear(Long casoId, CrearTareaRequest request, UsuarioInterno creador) {
        Caso caso = casoRepository.findById(casoId)
                .orElseThrow(() -> new IllegalArgumentException("Caso no encontrado con ID: " + casoId));
        UsuarioInterno asignado = usuarioInternoRepository.findById(request.usuarioAsignadoId())
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con ID: " + request.usuarioAsignadoId()));

        Tarea tarea = new Tarea();
        tarea.setCaso(caso);
        tarea.setTitulo(request.titulo());
        tarea.setDescripcion(request.descripcion());
        tarea.setFechaVencimiento(request.fechaVencimiento());
        tarea.setPrioridad(request.prioridad() != null ? request.prioridad() : PrioridadTarea.MEDIA);
        tarea.setUsuarioAsignado(asignado);
        tarea.setUsuarioCreador(creador);
        tareaRepository.save(tarea);

        registroSistemaService.registrar(
                TipoRegistroSistema.GESTION_TAREAS,
                "'%s' creó la tarea '%s' en el caso #%d, asignada a '%s'"
                        .formatted(creador.getNombre(), tarea.getTitulo(), caso.getId(), asignado.getNombre()),
                true);

        return TareaResponse.desde(tarea);
    }

    @Transactional
    public TareaResponse actualizar(Long tareaId, ActualizarTareaRequest request, UsuarioInterno actor) {
        Tarea tarea = tareaRepository.findById(tareaId)
                .orElseThrow(() -> new IllegalArgumentException("Tarea no encontrada con ID: " + tareaId));

        tarea.setTitulo(request.titulo());
        tarea.setDescripcion(request.descripcion());
        tarea.setFechaVencimiento(request.fechaVencimiento());
        if (request.prioridad() != null) {
            tarea.setPrioridad(request.prioridad());
        }
        if (request.usuarioAsignadoId() != null
                && !request.usuarioAsignadoId().equals(tarea.getUsuarioAsignado().getId())) {
            UsuarioInterno nuevoAsignado = usuarioInternoRepository.findById(request.usuarioAsignadoId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Usuario no encontrado con ID: " + request.usuarioAsignadoId()));
            tarea.setUsuarioAsignado(nuevoAsignado);
        }
        tareaRepository.save(tarea);

        registroSistemaService.registrar(
                TipoRegistroSistema.GESTION_TAREAS,
                "'%s' editó la tarea '%s' (ahora asignada a '%s')"
                        .formatted(actor.getNombre(), tarea.getTitulo(), tarea.getUsuarioAsignado().getNombre()),
                true);

        return TareaResponse.desde(tarea);
    }

    @Transactional
    public TareaResponse completar(Long tareaId, boolean completada, UsuarioInterno actor) {
        Tarea tarea = tareaRepository.findById(tareaId)
                .orElseThrow(() -> new IllegalArgumentException("Tarea no encontrada con ID: " + tareaId));

        tarea.setCompletada(completada);
        tarea.setFechaCompletada(completada ? LocalDateTime.now() : null);
        tareaRepository.save(tarea);

        registroSistemaService.registrar(
                TipoRegistroSistema.GESTION_TAREAS,
                "'%s' marcó la tarea '%s' como %s"
                        .formatted(actor.getNombre(), tarea.getTitulo(), completada ? "completada" : "pendiente"),
                true);

        return TareaResponse.desde(tarea);
    }

    @Transactional
    public void eliminar(Long tareaId, UsuarioInterno actor) {
        Tarea tarea = tareaRepository.findById(tareaId)
                .orElseThrow(() -> new IllegalArgumentException("Tarea no encontrada con ID: " + tareaId));
        String titulo = tarea.getTitulo();
        tareaRepository.delete(tarea);

        registroSistemaService.registrar(
                TipoRegistroSistema.GESTION_TAREAS,
                "'%s' eliminó la tarea '%s'".formatted(actor.getNombre(), titulo),
                true);
    }
}
