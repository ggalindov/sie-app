package sie.siejuridicos.solicitud.dto;

import sie.siejuridicos.solicitud.EstadoSolicitud;
import sie.siejuridicos.solicitud.OrigenSolicitud;
import sie.siejuridicos.solicitud.Solicitud;

import java.time.LocalDateTime;

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
        LocalDateTime fechaCita
) {
    public static SolicitudResponse desde(Solicitud solicitud) {
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
                solicitud.getFechaCita()
        );
    }
}
