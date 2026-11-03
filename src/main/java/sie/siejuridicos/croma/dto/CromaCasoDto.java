package sie.siejuridicos.croma.dto;

import java.time.LocalDate;

public record CromaCasoDto(
        String radicado,
        String despacho,
        String departamento,
        LocalDate fechaInicio,
        LocalDate fechaUltimaActuacion,
        String partes,
        boolean esPrivado
) {
}
