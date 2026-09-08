package sie.siejuridicos.solicitud;

import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sie.siejuridicos.common.exception.EntidadInvalidaException;
import sie.siejuridicos.common.exception.ErroresBaseDatos;
import sie.siejuridicos.common.exception.RecursoNoEncontradoException;
import sie.siejuridicos.correo.EmailService;
import sie.siejuridicos.marketing.SuscriptorMarketingService;
import sie.siejuridicos.security.UsuarioInternoPrincipal;
import sie.siejuridicos.solicitud.dto.AgendarCitaRequest;
import sie.siejuridicos.solicitud.dto.CrearSolicitudRequest;
import sie.siejuridicos.solicitud.dto.ResponsableReunionResponse;
import sie.siejuridicos.solicitud.dto.SolicitudResponse;
import sie.siejuridicos.usuario.RolUsuario;
import sie.siejuridicos.usuario.UsuarioInterno;
import sie.siejuridicos.usuario.UsuarioInternoRepository;
import sie.siejuridicos.whatsapp.WhatsAppService;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class SolicitudService {

    private final SolicitudRepository solicitudRepository;
    private final UsuarioInternoRepository usuarioInternoRepository;
    private final SuscriptorMarketingService suscriptorMarketingService;
    private final EmailService emailService;
    private final WhatsAppService whatsAppService;

    public SolicitudService(SolicitudRepository solicitudRepository,
                             UsuarioInternoRepository usuarioInternoRepository,
                             SuscriptorMarketingService suscriptorMarketingService,
                             EmailService emailService,
                             WhatsAppService whatsAppService) {
        this.solicitudRepository = solicitudRepository;
        this.usuarioInternoRepository = usuarioInternoRepository;
        this.suscriptorMarketingService = suscriptorMarketingService;
        this.emailService = emailService;
        this.whatsAppService = whatsAppService;
    }

    // @Transactional (sin readOnly) es necesario aquí: fn_crear_solicitud hace un INSERT
    // internamente, y el valor por defecto readOnly=true de los métodos de repositorio de
    // Spring Data JPA haría que Postgres rechace la escritura dentro de la función.
    @Transactional
    public SolicitudResponse crear(CrearSolicitudRequest request) {
        try {
            Solicitud creada = solicitudRepository.crearSolicitud(
                    request.nombre(),
                    request.correo(),
                    request.telefono(),
                    request.mensaje(),
                    OrigenSolicitud.FORMULARIO.name()
            );

            if (request.aceptaMarketing()) {
                suscriptorMarketingService.suscribir(request.nombre(), request.correo());
            }

            emailService.enviarConfirmacionYPromocionSolicitud(creada);
            emailService.enviarNotificacionAdminNuevaSolicitud(creada);
            whatsAppService.enviarNotificacionAdminNuevaSolicitud(
                    creada.getNombre(), creada.getCorreo(), creada.getTelefono(), creada.getMensaje());

            return SolicitudResponse.desde(creada);
        } catch (DataAccessException ex) {
            throw ErroresBaseDatos.traducir(ex);
        }
    }

    @Transactional(readOnly = true)
    public List<SolicitudResponse> listar(EstadoSolicitud estado, LocalDate desde, LocalDate hasta) {
        Specification<Solicitud> spec = fetchAbogadoAsignado();

        if (estado != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("estado"), estado));
        }
        if (desde != null) {
            LocalDateTime desdeInicio = desde.atStartOfDay();
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("fechaCreacion"), desdeInicio));
        }
        if (hasta != null) {
            LocalDateTime hastaFin = hasta.plusDays(1).atStartOfDay();
            spec = spec.and((root, query, cb) -> cb.lessThan(root.get("fechaCreacion"), hastaFin));
        }

        return solicitudRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "fechaCreacion")).stream()
                .map(SolicitudResponse::desde)
                .toList();
    }

    // Calendario de reuniones (ver SolicitudAdminController.calendario): a diferencia de
    // listar() -- que sigue mostrando TODAS las solicitudes a cualquier rol, sin cambios --
    // esta consulta sí queda acotada por rol. ADMIN_GENERAL ve las reuniones de todos los
    // abogados; un ABOGADO solo ve las propias, sin importar qué filtroAbogadoId le llegue
    // por query param (nunca se confía en un filtro que el propio cliente controla para
    // decidir visibilidad -- eso lo decide siempre el rol real del token).
    @Transactional(readOnly = true)
    public List<SolicitudResponse> listarCalendario(LocalDate desde, LocalDate hasta, Long filtroAbogadoId,
                                                      UsuarioInternoPrincipal actor) {
        LocalDateTime desdeInicio = desde.atStartOfDay();
        LocalDateTime hastaFin = hasta.plusDays(1).atStartOfDay();

        Specification<Solicitud> spec = ((Specification<Solicitud>) (root, query, cb) -> cb.and(
                cb.isNotNull(root.get("fechaCita")),
                cb.greaterThanOrEqualTo(root.get("fechaCita"), desdeInicio),
                cb.lessThan(root.get("fechaCita"), hastaFin)
        )).and(fetchAbogadoAsignado());

        boolean esAbogado = actor.getUsuario().getRol() == RolUsuario.ABOGADO;
        Long abogadoObligatorio = esAbogado ? actor.getId() : filtroAbogadoId;
        if (abogadoObligatorio != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("abogadoAsignado").get("id"), abogadoObligatorio));
        }

        return solicitudRepository.findAll(spec, Sort.by(Sort.Direction.ASC, "fechaCita")).stream()
                .map(SolicitudResponse::desde)
                .toList();
    }

    // N+1 encontrado en auditoría: SolicitudResponse.desde() lee
    // solicitud.getAbogadoAsignado().getNombre() para cada fila con responsable asignado, y
    // como la asociación es @ManyToOne(LAZY) sin este fetch, cada solicitud con reunión
    // agendada disparaba una consulta extra a usuarios_internos en cada carga de
    // /admin/solicitudes, /admin/calendario y en la exportación a Excel. Seguro de anexar en
    // ambos métodos: ninguno pagina con Pageable (solo Sort), así que JpaSpecificationExecutor
    // nunca ejecuta una query de conteo aparte donde un fetch join sería inválido/redundante.
    private static Specification<Solicitud> fetchAbogadoAsignado() {
        return (root, query, cb) -> {
            // abogadoAsignado es @ManyToOne: el fetch nunca duplica filas (a diferencia de un
            // fetch sobre una colección @OneToMany), así que no hace falta query.distinct().
            root.fetch("abogadoAsignado", jakarta.persistence.criteria.JoinType.LEFT);
            return cb.conjunction();
        };
    }

    // Usuarios internos activos entre los que ADMIN_GENERAL puede elegir al agendar una
    // reunión (ver AgendarCitaRequest.abogadoId). Un ABOGADO no necesita esta lista: siempre
    // queda asignado a sí mismo (ver agendarCita), nunca elige.
    @Transactional(readOnly = true)
    public List<ResponsableReunionResponse> listarResponsables() {
        return usuarioInternoRepository.findByActivoTrueOrderByNombreAsc().stream()
                .map(ResponsableReunionResponse::desde)
                .toList();
    }

    @Transactional
    public SolicitudResponse actualizarEstado(Long id, EstadoSolicitud nuevoEstado) {
        try {
            Solicitud actualizada = solicitudRepository.actualizarEstado(id, nuevoEstado.name());
            return SolicitudResponse.desde(actualizada);
        } catch (DataAccessException ex) {
            throw ErroresBaseDatos.traducir(ex);
        }
    }

    // Agenda (o reprograma) la reunión de una solicitud. Quién queda como responsable
    // depende de quién agenda, no de lo que venga en el request -- un ABOGADO no puede
    // asignarle su reunión a otra persona ni dejarla sin dueño (eso rompería la regla del
    // calendario: "cada abogado ve solo las suyas"), así que request.abogadoId() se ignora
    // por completo cuando el actor es ABOGADO. Solo ADMIN_GENERAL elige responsable, y debe
    // ser un usuario interno activo real (se valida contra la base, nunca se confía en el id
    // recibido a ciegas).
    @Transactional
    public SolicitudResponse agendarCita(Long id, AgendarCitaRequest request, UsuarioInternoPrincipal actor) {
        Solicitud solicitud = solicitudRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la solicitud con id " + id));

        UsuarioInterno responsable = resolverResponsable(request.abogadoId(), actor);

        solicitud.setCorreo(request.correo());
        // telefono es opcional en el request: si no se manda (o llega en blanco), se
        // conserva el que ya tenía la solicitud en vez de borrarlo.
        if (request.telefono() != null && !request.telefono().isBlank()) {
            solicitud.setTelefono(request.telefono());
        }
        aplicarTipoReunion(solicitud, request);
        solicitud.setAbogadoAsignado(responsable);
        solicitud.setFechaCita(request.fechaHora());
        // si se reprograma una cita cuyo recordatorio ya se había enviado, se rearma para la nueva fecha
        solicitud.setRecordatorioEnviado(false);
        Solicitud actualizada = solicitudRepository.save(solicitud);

        emailService.enviarConfirmacionCita(actualizada);
        emailService.enviarNotificacionAdminNuevaCita(actualizada);
        whatsAppService.enviarConfirmacionCita(
                actualizada.getNombre(), actualizada.getTelefono(),
                actualizada.getFechaCita(), detalleAccesoParaWhatsApp(actualizada));

        return SolicitudResponse.desde(actualizada);
    }

    // Valida y aplica linkReunion/lugarReunion segun tipoReunion -- son mutuamente
    // excluyentes: una reunion VIRTUAL nunca guarda un lugar, una PRESENCIAL nunca guarda un
    // link (asi los correos y el WhatsApp no tienen que volver a decidir cual mostrar, ya
    // viene resuelto desde aqui). El link, cuando es VIRTUAL, tiene que ser de verdad un
    // enlace de Google Meet o Zoom -- pedido explicito: no cualquier URL sirve como "reunion
    // virtual" (evita que alguien pegue por error el link del sitio, un Drive, etc).
    private void aplicarTipoReunion(Solicitud solicitud, AgendarCitaRequest request) {
        if (request.tipoReunion() == TipoReunion.VIRTUAL) {
            if (request.linkReunion() == null || request.linkReunion().isBlank()) {
                throw new EntidadInvalidaException("El link de la reunión virtual es obligatorio.");
            }
            if (!esLinkDeMeetOZoom(request.linkReunion())) {
                throw new EntidadInvalidaException(
                        "El link de la reunión debe ser de Google Meet (meet.google.com) o Zoom (zoom.us).");
            }
            solicitud.setLinkReunion(request.linkReunion().strip());
            solicitud.setLugarReunion(null);
        } else {
            if (request.lugarReunion() == null || request.lugarReunion().isBlank()) {
                throw new EntidadInvalidaException("El lugar de la reunión presencial es obligatorio.");
            }
            solicitud.setLugarReunion(request.lugarReunion().strip());
            solicitud.setLinkReunion(null);
        }
        solicitud.setTipoReunion(request.tipoReunion());
    }

    private static boolean esLinkDeMeetOZoom(String link) {
        try {
            URI uri = new URI(link.strip());
            String esquema = uri.getScheme();
            String host = uri.getHost();
            if (host == null || esquema == null || (!esquema.equalsIgnoreCase("http") && !esquema.equalsIgnoreCase("https"))) {
                return false;
            }
            host = host.toLowerCase(Locale.ROOT);
            boolean hostValido = host.equals("meet.google.com")
                    || host.endsWith(".zoom.us")
                    || host.equals("zoom.us");
            if (!hostValido) {
                return false;
            }
            // El host correcto no basta -- bug real encontrado en auditoría: los botones de
            // "acceso rápido" del panel (agendar-reunion-modal.tsx) prellenan
            // "https://meet.google.com/" y "https://zoom.us/j/", SIN código de reunión, y
            // ambos pasaban esta validación con solo el chequeo de host. Si el admin confirma
            // sin pegar el link real, el cliente recibe por correo/WhatsApp un "link de
            // acceso" que no lleva a ninguna reunión concreta. Se exige un código real
            // (último segmento de la ruta con longitud mínima) después del host.
            String path = uri.getPath();
            if (path == null) {
                return false;
            }
            String[] segmentos = path.split("/");
            String ultimoSegmento = segmentos.length > 0 ? segmentos[segmentos.length - 1] : "";
            return ultimoSegmento.length() >= 3;
        } catch (URISyntaxException | IllegalArgumentException ex) {
            return false;
        }
    }

    // El WhatsApp usa una sola plantilla ya aprobada con una variable de "detalle de acceso"
    // (ver WhatsAppService.enviarConfirmacionCita / DEPLOY.md 2.5): para una reunion virtual
    // esa variable trae el link, para una presencial la direccion -- asi no hace falta una
    // segunda plantilla aprobada aparte solo para reuniones presenciales. La frase siempre
    // arranca diciendo si es virtual o presencial (pedido explicito): a diferencia del correo
    // -- que ya lo deja claro con un boton dorado o un bloque de direccion aparte -- en
    // WhatsApp todo es texto plano dentro del mismo renglon, así que sin esa palabra el
    // cliente no tiene cómo distinguir un link de una dirección con solo leer el mensaje.
    private static String detalleAccesoParaWhatsApp(Solicitud solicitud) {
        if (solicitud.getTipoReunion() == TipoReunion.PRESENCIAL) {
            return "Será presencial, en: " + solicitud.getLugarReunion();
        }
        return "Será virtual, únete aquí: " + solicitud.getLinkReunion();
    }

    private UsuarioInterno resolverResponsable(Long abogadoIdSolicitado, UsuarioInternoPrincipal actor) {
        if (actor.getUsuario().getRol() == RolUsuario.ABOGADO) {
            return actor.getUsuario();
        }
        if (abogadoIdSolicitado == null) {
            throw new EntidadInvalidaException("Selecciona el responsable de la reunión.");
        }
        UsuarioInterno responsable = usuarioInternoRepository.findById(abogadoIdSolicitado)
                .orElseThrow(() -> new EntidadInvalidaException("El responsable seleccionado no existe."));
        if (!responsable.isActivo()) {
            throw new EntidadInvalidaException("El responsable seleccionado no está activo.");
        }
        return responsable;
    }
}
