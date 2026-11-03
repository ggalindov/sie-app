package sie.siejuridicos.croma.dto;

import java.time.OffsetDateTime;

public record CromaMemoriaResumenDto(
        Long casoId,
        String radicado,
        OffsetDateTime fechaExtraccion,
        OffsetDateTime fechaExpiracion,
        int diasParaBorrar,
        String guardadoHaceTexto
) {
}
