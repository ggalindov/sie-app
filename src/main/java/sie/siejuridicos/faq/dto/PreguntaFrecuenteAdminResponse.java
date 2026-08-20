package sie.siejuridicos.faq.dto;

import sie.siejuridicos.faq.EstadoPreguntaFrecuente;
import sie.siejuridicos.faq.PreguntaFrecuente;

import java.time.LocalDateTime;

public record PreguntaFrecuenteAdminResponse(
        Long id,
        String preguntaEjemplo,
        String respuestaSugerida,
        String respuestaFinal,
        Integer conteo,
        EstadoPreguntaFrecuente estado,
        LocalDateTime fechaPrimeraVez,
        LocalDateTime fechaActualizacion
) {
    public static PreguntaFrecuenteAdminResponse desde(PreguntaFrecuente pregunta) {
        return new PreguntaFrecuenteAdminResponse(
                pregunta.getId(),
                pregunta.getPreguntaEjemplo(),
                pregunta.getRespuestaSugerida(),
                pregunta.getRespuestaFinal(),
                pregunta.getConteo(),
                pregunta.getEstado(),
                pregunta.getFechaPrimeraVez(),
                pregunta.getFechaActualizacion()
        );
    }
}
