package sie.siejuridicos.pqrs.dto;

import sie.siejuridicos.pqrs.EstadoPqrs;
import sie.siejuridicos.pqrs.Pqrs;
import sie.siejuridicos.pqrs.TipoPqrs;

import java.time.LocalDateTime;

// Vista pública por radicado: nunca incluye el correo/teléfono del remitente (ya los sabe
// quien consulta, pero no hace falta devolverlos) ni quién la respondió internamente.
public record PqrsConsultaResponse(
        String radicado,
        TipoPqrs tipo,
        String asunto,
        String descripcion,
        EstadoPqrs estado,
        String respuesta,
        LocalDateTime fechaCreacion,
        LocalDateTime fechaLimiteRespuesta,
        LocalDateTime fechaRespuesta
) {
    public static PqrsConsultaResponse desde(Pqrs pqrs) {
        return new PqrsConsultaResponse(
                pqrs.getRadicado(),
                pqrs.getTipo(),
                pqrs.getAsunto(),
                pqrs.getDescripcion(),
                pqrs.getEstado(),
                pqrs.getRespuesta(),
                pqrs.getFechaCreacion(),
                pqrs.getFechaLimiteRespuesta(),
                pqrs.getFechaRespuesta()
        );
    }
}
