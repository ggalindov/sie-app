package sie.siejuridicos.caso.dto;

import sie.siejuridicos.caso.Caso;
import sie.siejuridicos.hojacalculo.dto.FilaCasoHoja;

import java.time.LocalDateTime;

// Respuesta pública de la consulta por radicado: deliberadamente NO incluye correo, teléfono
// ni notas internas del cliente (mismo criterio de privacidad que TestimonioResponse).
//
// estadoDisponible=false representa un estado normal, no un error: el radicado está
// registrado en nuestro sistema (por eso se pudo consultar) pero la firma todavía no cargó
// esa fila en su Google Sheets. En ese caso los campos de la hoja vienen en null.
public record CasoConsultaResponse(
        String radicadoId,
        boolean estadoDisponible,
        String despachoJudicial,
        String informacionCaso,
        String tipoCaso,
        String ultimaDecision,
        String estado,
        String fechaActualizacionHoja,
        LocalDateTime fechaRegistro
) {
    public static CasoConsultaResponse sinEstadoDisponible(Caso caso) {
        return new CasoConsultaResponse(
                caso.getRadicadoId(), false, null, null, null, null, null, null, caso.getFechaCreacion());
    }

    public static CasoConsultaResponse desde(Caso caso, FilaCasoHoja fila) {
        return new CasoConsultaResponse(
                caso.getRadicadoId(),
                true,
                fila.despachoJudicial(),
                fila.informacionCaso(),
                fila.tipoCaso(),
                fila.ultimaDecision(),
                fila.estado(),
                fila.fechaActualizacion(),
                caso.getFechaCreacion()
        );
    }
}
