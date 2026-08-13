package sie.siejuridicos.solicitud;

import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sie.siejuridicos.common.exception.ErroresBaseDatos;
import sie.siejuridicos.common.exception.RecursoNoEncontradoException;
import sie.siejuridicos.correo.EmailService;
import sie.siejuridicos.marketing.SuscriptorMarketingRepository;
import sie.siejuridicos.solicitud.dto.CrearSolicitudRequest;
import sie.siejuridicos.solicitud.dto.SolicitudResponse;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class SolicitudService {

    private final SolicitudRepository solicitudRepository;
    private final SuscriptorMarketingRepository suscriptorMarketingRepository;
    private final EmailService emailService;

    public SolicitudService(SolicitudRepository solicitudRepository,
                             SuscriptorMarketingRepository suscriptorMarketingRepository,
                             EmailService emailService) {
        this.solicitudRepository = solicitudRepository;
        this.suscriptorMarketingRepository = suscriptorMarketingRepository;
        this.emailService = emailService;
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
                suscriptorMarketingRepository.suscribirSiNoExiste(request.nombre(), request.correo());
            }

            emailService.enviarConfirmacionYPromocionSolicitud(creada);
            emailService.enviarNotificacionAdminNuevaSolicitud(creada);

            return SolicitudResponse.desde(creada);
        } catch (DataAccessException ex) {
            throw ErroresBaseDatos.traducir(ex);
        }
    }

    @Transactional(readOnly = true)
    public List<SolicitudResponse> listar(EstadoSolicitud estado, LocalDate desde, LocalDate hasta) {
        Specification<Solicitud> spec = (root, query, cb) -> cb.conjunction();

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

    @Transactional
    public SolicitudResponse actualizarEstado(Long id, EstadoSolicitud nuevoEstado) {
        try {
            Solicitud actualizada = solicitudRepository.actualizarEstado(id, nuevoEstado.name());
            return SolicitudResponse.desde(actualizada);
        } catch (DataAccessException ex) {
            throw ErroresBaseDatos.traducir(ex);
        }
    }

    @Transactional
    public SolicitudResponse agendarCita(Long id, LocalDateTime fechaHora) {
        Solicitud solicitud = solicitudRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la solicitud con id " + id));

        solicitud.setFechaCita(fechaHora);
        // si se reprograma una cita cuyo recordatorio ya se había enviado, se rearma para la nueva fecha
        solicitud.setRecordatorioEnviado(false);
        Solicitud actualizada = solicitudRepository.save(solicitud);

        emailService.enviarConfirmacionCita(actualizada);
        emailService.enviarNotificacionAdminNuevaCita(actualizada);

        return SolicitudResponse.desde(actualizada);
    }
}
