package sie.siejuridicos.crm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sie.siejuridicos.caso.Caso;
import sie.siejuridicos.caso.CasoRepository;
import sie.siejuridicos.cobro.ClienteCobro;
import sie.siejuridicos.cobro.ClienteCobroRepository;
import sie.siejuridicos.cobro.CobroService;
import sie.siejuridicos.common.cifrado.CifradoService;
import sie.siejuridicos.crm.dto.ActividadCrmResponse;
import sie.siejuridicos.crm.dto.ActualizarClienteCrmRequest;
import sie.siejuridicos.crm.dto.CambiarEtapaPipelineRequest;
import sie.siejuridicos.crm.dto.ClienteCrmDetalleResponse;
import sie.siejuridicos.crm.dto.ClienteCrmPaginaResponse;
import sie.siejuridicos.crm.dto.ClienteCrmResponse;
import sie.siejuridicos.crm.dto.ConvertirProspectoRequest;
import sie.siejuridicos.crm.dto.CrearActividadCrmRequest;
import sie.siejuridicos.crm.dto.CrearClienteCrmRequest;
import sie.siejuridicos.crm.dto.CrearTareaCrmRequest;
import sie.siejuridicos.crm.dto.CrmDashboardResponse;
import sie.siejuridicos.crm.dto.ItemPipelineResponse;
import sie.siejuridicos.crm.dto.TareaCrmResponse;
import sie.siejuridicos.registro.RegistroSistemaService;
import sie.siejuridicos.registro.TipoRegistroSistema;
import sie.siejuridicos.solicitud.EstadoSolicitud;
import sie.siejuridicos.solicitud.Solicitud;
import sie.siejuridicos.solicitud.SolicitudRepository;
import sie.siejuridicos.whatsapp.WhatsAppService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CrmService {

    private static final Logger log = LoggerFactory.getLogger(CrmService.class);

    private final ClienteCrmRepository clienteCrmRepository;
    private final ActividadCrmRepository actividadCrmRepository;
    private final TareaCrmRepository tareaCrmRepository;
    private final SolicitudRepository solicitudRepository;
    private final CasoRepository casoRepository;
    private final ClienteCobroRepository clienteCobroRepository;
    private final CifradoService cifradoService;
    private final RegistroSistemaService registroSistemaService;

    public CrmService(ClienteCrmRepository clienteCrmRepository,
                      ActividadCrmRepository actividadCrmRepository,
                      TareaCrmRepository tareaCrmRepository,
                      SolicitudRepository solicitudRepository,
                      CasoRepository casoRepository,
                      ClienteCobroRepository clienteCobroRepository,
                      CifradoService cifradoService,
                      RegistroSistemaService registroSistemaService) {
        this.clienteCrmRepository = clienteCrmRepository;
        this.actividadCrmRepository = actividadCrmRepository;
        this.tareaCrmRepository = tareaCrmRepository;
        this.solicitudRepository = solicitudRepository;
        this.casoRepository = casoRepository;
        this.clienteCobroRepository = clienteCobroRepository;
        this.cifradoService = cifradoService;
        this.registroSistemaService = registroSistemaService;
    }

    // =========================================================================
    // CLIENTES CRM 360°
    // =========================================================================

    @Transactional(readOnly = true)
    public ClienteCrmPaginaResponse listarClientes(String busqueda, EstadoClienteCrm estado, TipoClienteCrm tipo,
                                                     Boolean pagoAlDia, int pagina, int tamanoPagina) {
        List<ClienteCrm> base = (estado != null)
                ? clienteCrmRepository.findByEstadoOrderByNombreAsc(estado)
                : clienteCrmRepository.findAllByOrderByFechaCreacionDesc();

        String q = (busqueda == null || busqueda.isBlank()) ? null : busqueda.strip().toLowerCase(Locale.ROOT);

        // nombre/cédula/correo/teléfono están cifrados en la base de datos (ver
        // ClienteCrm/CampoCifradoConverter): no hay forma de filtrarlos con un LIKE en SQL,
        // es la razón de ser de los *Hash (igualdad exacta, no búsqueda parcial). El filtro
        // de texto libre necesariamente descifra en memoria. Lo que sí se corrige abajo es
        // dejar de construir la ficha completa (2 consultas extra por cliente: casos y
        // cobros) para los clientes que no van en la página pedida -- antes se hacía para
        // los 225+ clientes en cada carga del directorio, sin importar cuántos se mostraran.
        List<ClienteCrm> filtrados = base.stream()
                .filter(c -> tipo == null || c.getTipo() == tipo)
                .filter(c -> {
                    if (q == null) return true;
                    return (c.getNombre() != null && c.getNombre().toLowerCase(Locale.ROOT).contains(q))
                            || (c.getCedulaNit() != null && c.getCedulaNit().toLowerCase(Locale.ROOT).contains(q))
                            || (c.getCorreo() != null && c.getCorreo().toLowerCase(Locale.ROOT).contains(q))
                            || (c.getTelefono() != null && c.getTelefono().toLowerCase(Locale.ROOT).contains(q))
                            || (c.getEtiqueta() != null && c.getEtiqueta().toLowerCase(Locale.ROOT).contains(q));
                })
                .toList();

        // Filtro "Estado de cobro" (pedido explícito del usuario: "ver quién me debe pagos"
        // sin tener que abrir la ficha de cada cliente, uno por uno). Un solo query bulk
        // (findByActivoTrueAndClienteCrmIdIsNotNull), no una consulta por cliente -- mismo
        // criterio "al día" que construirClienteResponse (todos sus cobros con
        // pagoEsteMes=true; sin cobros vinculados también cuenta como al día).
        if (pagoAlDia != null) {
            Map<Long, List<ClienteCobro>> cobrosPorCliente = clienteCobroRepository
                    .findByActivoTrueAndClienteCrmIdIsNotNull().stream()
                    .collect(Collectors.groupingBy(ClienteCobro::getClienteCrmId));
            filtrados = filtrados.stream()
                    .filter(c -> {
                        boolean alDia = cobrosPorCliente.getOrDefault(c.getId(), List.of()).stream()
                                .allMatch(cob -> Boolean.TRUE.equals(cob.getPagoEsteMes()));
                        return alDia == pagoAlDia;
                    })
                    .toList();
        }

        int tam = Math.max(1, Math.min(tamanoPagina, 100));
        int totalPaginas = Math.max(1, (int) Math.ceil(filtrados.size() / (double) tam));
        int paginaSegura = Math.max(0, Math.min(pagina, totalPaginas - 1));

        List<ClienteCrmResponse> contenido = filtrados.stream()
                .skip((long) paginaSegura * tam)
                .limit(tam)
                .map(this::construirClienteResponse)
                .toList();

        return new ClienteCrmPaginaResponse(contenido, paginaSegura, totalPaginas, filtrados.size());
    }

    private ClienteCrmResponse construirClienteResponse(ClienteCrm c) {
        List<Caso> casos = casoRepository.findByClienteClienteCrmId(c.getId());
        List<ClienteCobro> cobros = clienteCobroRepository.findByClienteCrmIdAndActivoTrue(c.getId());

        boolean alDia = cobros.stream().allMatch(cob -> Boolean.TRUE.equals(cob.getPagoEsteMes()));

        return ClienteCrmResponse.desde(c, casos.size(), cobros.size(), alDia);
    }

    @Transactional(readOnly = true)
    public ClienteCrmDetalleResponse obtenerDetalle(Long id) {
        ClienteCrm c = clienteCrmRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cliente CRM no encontrado: " + id));

        ClienteCrmResponse resumen = construirClienteResponse(c);

        // Expedientes judiciales asociados
        List<Caso> casosEntidad = casoRepository.findByClienteClienteCrmId(c.getId());
        List<ClienteCrmDetalleResponse.CasoVinculadoDto> casos = casosEntidad.stream()
                .map(cas -> new ClienteCrmDetalleResponse.CasoVinculadoDto(
                        cas.getId(),
                        cas.getRadicadoId(),
                        cas.getFuente() != null ? cas.getFuente().getNombreVisible() : "General",
                        cas.getNumeroCaso(),
                        // Mismo criterio que CasoAdminResponse.desde(): el nombre de la hoja si
                        // existe, si no el del Cliente compartido.
                        cas.getNombreEnHoja() != null ? cas.getNombreEnHoja() : cas.getCliente().getNombre(),
                        cas.getNotasInternas(),
                        cas.isCorreoEnviado(),
                        cas.isWhatsappEnviado()
                ))
                .toList();

        // Cobros mensuales asociados
        List<ClienteCobro> cobrosEntidad = clienteCobroRepository.findByClienteCrmIdAndActivoTrue(c.getId());
        List<ClienteCrmDetalleResponse.CobroVinculadoDto> cobros = cobrosEntidad.stream()
                .map(cob -> new ClienteCrmDetalleResponse.CobroVinculadoDto(
                        cob.getId(),
                        cob.getTipo() != null ? cob.getTipo().name() : "GENERAL",
                        cob.getNumeroFila(),
                        cob.getHonorarios(),
                        cob.getPagoEsteMes(),
                        cob.getRespondioMensaje(),
                        cob.getFechaUltimoRecordatorio()
                ))
                .toList();

        // Citas agendadas asociadas
        List<Solicitud> solicitudesCitas = solicitudRepository.findByClienteCrmId(c.getId());
        List<ClienteCrmDetalleResponse.CitaVinculadaDto> citas = solicitudesCitas.stream()
                .filter(s -> s.getFechaCita() != null)
                .map(s -> new ClienteCrmDetalleResponse.CitaVinculadaDto(
                        s.getId(),
                        s.getFechaCita(),
                        s.getTipoReunion() != null ? s.getTipoReunion().name() : "VIRTUAL",
                        s.getLinkReunion(),
                        s.getLugarReunion()
                ))
                .toList();

        // Bitácora de actividades
        List<ActividadCrmResponse> actividades = actividadCrmRepository
                .findByClienteCrmIdOrderByFechaActividadDesc(c.getId())
                .stream()
                .map(ActividadCrmResponse::desde)
                .toList();

        // Tareas pendientes o completadas
        List<TareaCrmResponse> tareas = tareaCrmRepository
                .findByClienteCrmIdOrderByFechaCreacionDesc(c.getId())
                .stream()
                .map(TareaCrmResponse::desde)
                .toList();

        return new ClienteCrmDetalleResponse(resumen, casos, cobros, citas, actividades, tareas);
    }

    @Transactional
    public ClienteCrmResponse crearCliente(CrearClienteCrmRequest request) {
        ClienteCrm c = new ClienteCrm();
        c.setTipo(request.tipo() != null ? request.tipo() : TipoClienteCrm.PERSONA_NATURAL);
        c.setNombre(request.nombre());
        c.setCedulaNit(request.cedulaNit());
        c.setCedulaNitHash(calcularHash(request.cedulaNit()));
        c.setCorreo(request.correo());
        c.setCorreoHash(calcularHash(request.correo()));
        c.setTelefono(request.telefono());
        c.setTelefonoHash(calcularTelefonoHash(request.telefono()));
        c.setDireccion(request.direccion());
        c.setCiudad(request.ciudad());
        c.setEstado(request.estado() != null ? request.estado() : EstadoClienteCrm.ACTIVO);
        c.setEtiqueta(request.etiqueta());
        c.setNotas(request.notas());

        clienteCrmRepository.save(c);

        // Registro de actividad inicial en la bitácora
        ActividadCrm act = new ActividadCrm();
        act.setClienteCrm(c);
        act.setTipo(TipoActividadCrm.NOTA_INTERNA);
        act.setTitulo("Cliente creado en CRM");
        act.setDescripcion("Registro inicial del cliente en el sistema CRM.");
        act.setUsuarioNombre("Sistema / Admin");
        actividadCrmRepository.save(act);

        registroSistemaService.registrar(
                TipoRegistroSistema.GESTION_CRM,
                "Cliente CRM creado: " + c.getNombre(),
                null,
                true);

        return construirClienteResponse(c);
    }

    @Transactional
    public ClienteCrmResponse actualizarCliente(Long id, ActualizarClienteCrmRequest request) {
        ClienteCrm c = clienteCrmRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cliente CRM no encontrado: " + id));

        if (request.tipo() != null) c.setTipo(request.tipo());
        if (request.nombre() != null) c.setNombre(request.nombre());
        if (request.cedulaNit() != null) {
            c.setCedulaNit(request.cedulaNit());
            c.setCedulaNitHash(calcularHash(request.cedulaNit()));
        }
        if (request.correo() != null) {
            c.setCorreo(request.correo());
            c.setCorreoHash(calcularHash(request.correo()));
        }
        if (request.telefono() != null) {
            c.setTelefono(request.telefono());
            c.setTelefonoHash(calcularTelefonoHash(request.telefono()));
        }
        if (request.direccion() != null) c.setDireccion(request.direccion());
        if (request.ciudad() != null) c.setCiudad(request.ciudad());
        if (request.estado() != null) c.setEstado(request.estado());
        if (request.etiqueta() != null) c.setEtiqueta(request.etiqueta());
        if (request.notas() != null) c.setNotas(request.notas());

        clienteCrmRepository.save(c);
        return construirClienteResponse(c);
    }

    @Transactional
    public void archivarCliente(Long id) {
        ClienteCrm c = clienteCrmRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cliente CRM no encontrado: " + id));
        c.setEstado(EstadoClienteCrm.INACTIVO);
        clienteCrmRepository.save(c);
    }

    // =========================================================================
    // PIPELINE DE OPORTUNIDADES / LEADS
    // =========================================================================

    @Transactional(readOnly = true)
    public List<ItemPipelineResponse> obtenerPipeline() {
        return solicitudRepository.findAllByOrderByFechaCreacionDesc().stream()
                .map(s -> new ItemPipelineResponse(
                        s.getId(),
                        s.getNombre(),
                        s.getCorreo(),
                        s.getTelefono(),
                        s.getMensaje(),
                        s.getEtapaPipeline() != null ? s.getEtapaPipeline() : EtapaPipeline.NUEVO,
                        s.getValorEstimado(),
                        s.getAreaPractica(),
                        s.getFechaCreacion(),
                        s.getClienteCrmId()
                ))
                .toList();
    }

    @Transactional
    public ItemPipelineResponse cambiarEtapaPipeline(Long solicitudId, CambiarEtapaPipelineRequest request) {
        Solicitud s = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new IllegalArgumentException("Solicitud no encontrada: " + solicitudId));

        EtapaPipeline etapaAnterior = s.getEtapaPipeline();
        s.setEtapaPipeline(request.nuevaEtapa());
        s.setFechaActualizacionEstado(LocalDateTime.now());

        if (request.nuevaEtapa() == EtapaPipeline.CONTRATADO) {
            s.setEstado(EstadoSolicitud.CERRADO);
        } else if (request.nuevaEtapa() == EtapaPipeline.CONTACTADO) {
            s.setEstado(EstadoSolicitud.CONTACTADO);
        }

        solicitudRepository.save(s);

        // Si tiene cliente CRM vinculado, registrar en su bitácora
        if (s.getClienteCrmId() != null) {
            clienteCrmRepository.findById(s.getClienteCrmId()).ifPresent(cliente -> {
                ActividadCrm act = new ActividadCrm();
                act.setClienteCrm(cliente);
                act.setSolicitudId(s.getId());
                act.setTipo(TipoActividadCrm.CAMBIO_ESTADO);
                act.setTitulo("Cambio de etapa: " + etapaAnterior + " -> " + request.nuevaEtapa());
                act.setDescripcion(request.nota() != null ? request.nota() : "Transición en el pipeline de oportunidades.");
                act.setUsuarioNombre(request.usuarioNombre() != null ? request.usuarioNombre() : "Admin");
                actividadCrmRepository.save(act);
            });
        }

        return new ItemPipelineResponse(
                s.getId(),
                s.getNombre(),
                s.getCorreo(),
                s.getTelefono(),
                s.getMensaje(),
                s.getEtapaPipeline(),
                s.getValorEstimado(),
                s.getAreaPractica(),
                s.getFechaCreacion(),
                s.getClienteCrmId()
        );
    }

    @Transactional
    public ClienteCrmResponse convertirProspectoACrm(Long solicitudId, ConvertirProspectoRequest request) {
        Solicitud s = solicitudRepository.findById(solicitudId)
                .orElseThrow(() -> new IllegalArgumentException("Solicitud no encontrada: " + solicitudId));

        // Si ya tenía cliente CRM, devolvemos el existente
        if (s.getClienteCrmId() != null) {
            ClienteCrm existente = clienteCrmRepository.findById(s.getClienteCrmId()).orElse(null);
            if (existente != null) {
                return construirClienteResponse(existente);
            }
        }

        // Bug real corregido aquí: antes SIEMPRE se creaba un ClienteCrm nuevo, incluso
        // cuando la misma persona ya tenía un cliente en el directorio (volvió a escribir
        // por el formulario público, o ya era cliente activo) -- eso fue lo que llenó el
        // directorio de duplicados reales con el mismo teléfono/correo (confirmado en
        // producción: "Yerika Alexandra Samaniego Arango" y "Yerika Samaniego", mismo
        // teléfono y correo, dos registros). Antes de crear, se busca por los mismos índices
        // ciegos que ya usa crearCliente() para esto mismo (telefonoHash primero, correoHash
        // de respaldo) -- si existe, se reutiliza y solo se vincula la solicitud nueva, en
        // vez de duplicar el registro.
        ClienteCrm existentePorContacto = buscarClienteExistentePorContacto(s.getTelefono(), s.getCorreo());
        if (existentePorContacto != null) {
            s.setClienteCrmId(existentePorContacto.getId());
            s.setEtapaPipeline(EtapaPipeline.CONTRATADO);
            s.setEstado(EstadoSolicitud.CERRADO);
            solicitudRepository.save(s);

            ActividadCrm actVinculo = new ActividadCrm();
            actVinculo.setClienteCrm(existentePorContacto);
            actVinculo.setSolicitudId(s.getId());
            actVinculo.setTipo(TipoActividadCrm.CAMBIO_ESTADO);
            actVinculo.setTitulo("Nueva solicitud vinculada a cliente existente");
            actVinculo.setDescripcion(
                    "Se vinculó una nueva solicitud contratada a este cliente ya registrado (mismo "
                            + "teléfono o correo), en vez de crear un duplicado en el directorio.");
            actVinculo.setUsuarioNombre("Admin");
            actividadCrmRepository.save(actVinculo);

            return construirClienteResponse(existentePorContacto);
        }

        ClienteCrm nuevo = new ClienteCrm();
        nuevo.setTipo(request.tipo() != null ? request.tipo() : TipoClienteCrm.PERSONA_NATURAL);
        nuevo.setNombre(s.getNombre());
        nuevo.setCorreo(s.getCorreo());
        nuevo.setCorreoHash(calcularHash(s.getCorreo()));
        nuevo.setTelefono(s.getTelefono());
        nuevo.setTelefonoHash(calcularTelefonoHash(s.getTelefono()));
        nuevo.setCedulaNit(request.cedulaNit());
        nuevo.setCedulaNitHash(calcularHash(request.cedulaNit()));
        nuevo.setDireccion(request.direccion());
        nuevo.setCiudad(request.ciudad());
        nuevo.setEstado(EstadoClienteCrm.ACTIVO);
        nuevo.setEtiqueta(request.etiqueta() != null ? request.etiqueta() : "CONVERTIDO_LEAD");
        nuevo.setNotas(request.notas() != null ? request.notas() : "Convertido desde solicitud web ID: " + s.getId());

        clienteCrmRepository.save(nuevo);

        s.setClienteCrmId(nuevo.getId());
        s.setEtapaPipeline(EtapaPipeline.CONTRATADO);
        s.setEstado(EstadoSolicitud.CERRADO);
        solicitudRepository.save(s);

        // Registrar en bitácora
        ActividadCrm act = new ActividadCrm();
        act.setClienteCrm(nuevo);
        act.setSolicitudId(s.getId());
        act.setTipo(TipoActividadCrm.CAMBIO_ESTADO);
        act.setTitulo("Prospecto convertido a Cliente Formal");
        act.setDescripcion("El prospecto fue contratado y vinculado al directorio CRM con expediente activo.");
        act.setUsuarioNombre("Admin");
        actividadCrmRepository.save(act);

        return construirClienteResponse(nuevo);
    }

    // =========================================================================
    // BITÁCORA Y TAREAS
    // =========================================================================

    @Transactional
    public ActividadCrmResponse registrarActividad(Long clienteId, CrearActividadCrmRequest request) {
        ClienteCrm cliente = clienteCrmRepository.findById(clienteId)
                .orElseThrow(() -> new IllegalArgumentException("Cliente CRM no encontrado: " + clienteId));

        ActividadCrm act = new ActividadCrm();
        act.setClienteCrm(cliente);
        act.setSolicitudId(request.solicitudId());
        act.setCasoId(request.casoId());
        act.setTipo(request.tipo() != null ? request.tipo() : TipoActividadCrm.NOTA_INTERNA);
        act.setTitulo(request.titulo());
        act.setDescripcion(request.descripcion());
        act.setUsuarioNombre(request.usuarioNombre() != null ? request.usuarioNombre() : "Admin");
        if (request.fechaActividad() != null) {
            act.setFechaActividad(request.fechaActividad());
        }

        actividadCrmRepository.save(act);
        return ActividadCrmResponse.desde(act);
    }

    @Transactional
    public TareaCrmResponse crearTarea(Long clienteId, CrearTareaCrmRequest request) {
        ClienteCrm cliente = clienteCrmRepository.findById(clienteId)
                .orElseThrow(() -> new IllegalArgumentException("Cliente CRM no encontrado: " + clienteId));

        TareaCrm tarea = new TareaCrm();
        tarea.setClienteCrm(cliente);
        tarea.setTitulo(request.titulo());
        tarea.setDescripcion(request.descripcion());
        tarea.setFechaVencimiento(request.fechaVencimiento());
        tarea.setPrioridad(request.prioridad() != null ? request.prioridad() : PrioridadTareaCrm.MEDIA);
        tarea.setUsuarioNombre(request.usuarioNombre() != null ? request.usuarioNombre() : "Admin");

        tareaCrmRepository.save(tarea);
        return TareaCrmResponse.desde(tarea);
    }

    @Transactional
    public TareaCrmResponse completarTarea(Long tareaId, boolean completada) {
        TareaCrm tarea = tareaCrmRepository.findById(tareaId)
                .orElseThrow(() -> new IllegalArgumentException("Tarea CRM no encontrada: " + tareaId));
        tarea.setCompletada(completada);
        tareaCrmRepository.save(tarea);
        return TareaCrmResponse.desde(tarea);
    }

    // =========================================================================
    // DASHBOARD & MÉTRICAS
    // =========================================================================

    @Transactional(readOnly = true)
    public CrmDashboardResponse obtenerDashboard() {
        long totalClientes = clienteCrmRepository.count();
        long casosActivos = casoRepository.count();

        List<Solicitud> solicitudes = solicitudRepository.findAll();
        long prospectosActivos = solicitudes.stream()
                .filter(s -> s.getEtapaPipeline() != EtapaPipeline.CONTRATADO && s.getEtapaPipeline() != EtapaPipeline.DESCARTADO)
                .count();

        BigDecimal valorTotal = solicitudes.stream()
                .filter(s -> s.getValorEstimado() != null)
                .map(Solicitud::getValorEstimado)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<ClienteCobro> cobrosActivos = clienteCobroRepository.findByActivoTrueOrderByNombreAsc();
        // Solo considerar a los clientes que realmente tienen un valor asignado (> 0).
        // Los casos sin costo ($0 o vacío) se omiten, contando únicamente aquellos con respuesta de pago pendiente.
        List<ClienteCobro> cobrosConCosto = cobrosActivos.stream()
                .filter(c -> CobroService.tieneCosto(c.getHonorarios()))
                .toList();

        long cobrosAlDia = cobrosConCosto.stream()
                .filter(c -> Boolean.TRUE.equals(c.getPagoEsteMes()))
                .count();

        long cobrosPendientes = cobrosConCosto.stream()
                .filter(c -> !Boolean.TRUE.equals(c.getPagoEsteMes()))
                .count();

        Map<String, Long> leadsPorEtapa = solicitudes.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getEtapaPipeline() != null ? s.getEtapaPipeline().name() : EtapaPipeline.NUEVO.name(),
                        LinkedHashMap::new,
                        Collectors.counting()
                ));

        return new CrmDashboardResponse(
                totalClientes,
                prospectosActivos,
                casosActivos,
                valorTotal,
                cobrosAlDia,
                cobrosPendientes,
                leadsPorEtapa
        );
    }

    // Usado por convertirProspectoACrm para evitar duplicados: mismo criterio de búsqueda
    // que ya usan los *Hash (índice ciego, igualdad exacta sobre el valor normalizado).
    // Teléfono primero (más confiable entre solicitudes del formulario público: el correo a
    // veces se escribe distinto -- mayúsculas, typos -- pero el celular normalizado rara vez
    // cambia), correo como respaldo si no hay teléfono o no hubo coincidencia.
    private ClienteCrm buscarClienteExistentePorContacto(String telefono, String correo) {
        String telHash = calcularTelefonoHash(telefono);
        if (telHash != null) {
            Optional<ClienteCrm> porTelefono = clienteCrmRepository.findByTelefonoHash(telHash);
            if (porTelefono.isPresent()) return porTelefono.get();
        }
        String correoHash = calcularHash(correo);
        if (correoHash != null) {
            Optional<ClienteCrm> porCorreo = clienteCrmRepository.findByCorreoHash(correoHash);
            if (porCorreo.isPresent()) return porCorreo.get();
        }
        return null;
    }

    private String calcularHash(String valor) {
        if (valor == null || valor.isBlank()) return null;
        return cifradoService.indiceCiego(valor.strip().toLowerCase(Locale.ROOT));
    }

    private String calcularTelefonoHash(String telefono) {
        String normalizado = WhatsAppService.normalizarCelular(telefono);
        return normalizado == null ? null : cifradoService.indiceCiego(normalizado);
    }
}
