package sie.siejuridicos.pqrs.dto;

import sie.siejuridicos.pqrs.EstadoPqrs;
import sie.siejuridicos.pqrs.Pqrs;
import sie.siejuridicos.pqrs.TipoPqrs;

import java.time.LocalDateTime;

public record PqrsAdminResponse(
        Long id,
        String radicado,
        TipoPqrs tipo,
        String nombre,
        String correo,
        String telefono,
        String asunto,
        String descripcion,
        EstadoPqrs estado,
        String respuesta,
        String respondidoPor,
        LocalDateTime fechaCreacion,
        LocalDateTime fechaLimiteRespuesta,
        LocalDateTime fechaRespuesta
) {
    public static PqrsAdminResponse desde(Pqrs pqrs) {
        return new PqrsAdminResponse(
                pqrs.getId(),
                pqrs.getRadicado(),
                pqrs.getTipo(),
                pqrs.getNombre(),
                pqrs.getCorreo(),
                pqrs.getTelefono(),
                pqrs.getAsunto(),
                pqrs.getDescripcion(),
                pqrs.getEstado(),
                pqrs.getRespuesta(),
                pqrs.getRespondidoPor(),
                pqrs.getFechaCreacion(),
                pqrs.getFechaLimiteRespuesta(),
                pqrs.getFechaRespuesta()
        );
    }
}
