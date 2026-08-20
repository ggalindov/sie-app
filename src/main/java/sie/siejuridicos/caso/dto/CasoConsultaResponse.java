package sie.siejuridicos.caso.dto;

import sie.siejuridicos.caso.Caso;
import sie.siejuridicos.caso.EtapaCaso;

import java.time.LocalDateTime;

// Respuesta pública de la consulta por código: deliberadamente NO incluye correo,
// teléfono ni notas internas del cliente (mismo criterio de privacidad que
// TestimonioResponse, que nunca expone el correo del testimonio en la vista pública).
public record CasoConsultaResponse(
        String codigoUnico,
        String tipoCaso,
        EtapaCaso etapa,
        LocalDateTime fechaCreacion,
        LocalDateTime fechaActualizacion
) {
    public static CasoConsultaResponse desde(Caso caso) {
        return new CasoConsultaResponse(
                caso.getCodigoUnico(),
                caso.getCategoria().getNombre(),
                caso.getEtapa(),
                caso.getFechaCreacion(),
                caso.getFechaActualizacion()
        );
    }
}
