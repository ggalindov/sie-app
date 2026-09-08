package sie.siejuridicos.solicitud.dto;

import sie.siejuridicos.solicitud.EstadoSolicitud;
import sie.siejuridicos.solicitud.OrigenSolicitud;
import sie.siejuridicos.solicitud.Solicitud;
import sie.siejuridicos.solicitud.TipoReunion;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

public record SolicitudResponse(
        Long id,
        String nombre,
        String correo,
        String telefono,
        String mensaje,
        OrigenSolicitud origen,
        EstadoSolicitud estado,
        String notasInternas,
        LocalDateTime fechaCreacion,
        LocalDateTime fechaActualizacionEstado,
        LocalDateTime fechaCita,
        TipoReunion tipoReunion,
        String linkReunion,
        String lugarReunion,
        List<ResponsableReunionResponse> responsables
) {
    public static SolicitudResponse desde(Solicitud solicitud) {
        // responsables es LAZY: recorrerlo aqui exige que la transaccion siga abierta
        // (@Transactional en el metodo que llama a este mapeo, ver SolicitudService) -- fuera
        // de una transaccion activa lanzaria LazyInitializationException.
        List<ResponsableReunionResponse> responsables = solicitud.getResponsables().stream()
                .map(ResponsableReunionResponse::desde)
                .sorted(Comparator.comparing(ResponsableReunionResponse::nombre))
                .toList();
        return new SolicitudResponse(
                solicitud.getId(),
                solicitud.getNombre(),
                solicitud.getCorreo(),
                solicitud.getTelefono(),
                solicitud.getMensaje(),
                solicitud.getOrigen(),
                solicitud.getEstado(),
                solicitud.getNotasInternas(),
                solicitud.getFechaCreacion(),
                solicitud.getFechaActualizacionEstado(),
                solicitud.getFechaCita(),
                solicitud.getTipoReunion(),
                solicitud.getLinkReunion(),
                solicitud.getLugarReunion(),
                responsables
        );
    }
}
