package sie.siejuridicos.pqrs.dto;

import sie.siejuridicos.pqrs.Pqrs;

import java.time.LocalDateTime;

// Respuesta inmediata al radicar: el único momento en que el remitente ve su radicado en
// pantalla además de recibirlo por correo (ver EmailService.enviarRadicadoPqrs).
public record PqrsCreadaResponse(
        String radicado,
        LocalDateTime fechaCreacion,
        LocalDateTime fechaLimiteRespuesta
) {
    public static PqrsCreadaResponse desde(Pqrs pqrs) {
        return new PqrsCreadaResponse(pqrs.getRadicado(), pqrs.getFechaCreacion(), pqrs.getFechaLimiteRespuesta());
    }
}
