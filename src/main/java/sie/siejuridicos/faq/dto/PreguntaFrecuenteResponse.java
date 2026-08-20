package sie.siejuridicos.faq.dto;

import sie.siejuridicos.faq.PreguntaFrecuente;

public record PreguntaFrecuenteResponse(
        Long id,
        String pregunta,
        String respuesta
) {
    public static PreguntaFrecuenteResponse desde(PreguntaFrecuente pregunta) {
        return new PreguntaFrecuenteResponse(pregunta.getId(), pregunta.getPreguntaEjemplo(), pregunta.getRespuestaFinal());
    }
}
