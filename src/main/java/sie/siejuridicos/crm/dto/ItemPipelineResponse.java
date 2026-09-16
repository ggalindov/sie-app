package sie.siejuridicos.crm.dto;

import sie.siejuridicos.crm.EtapaPipeline;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ItemPipelineResponse(
        Long id,
        String nombre,
        String correo,
        String telefono,
        String mensaje,
        EtapaPipeline etapa,
        BigDecimal valorEstimado,
        String areaPractica,
        LocalDateTime fechaCreacion,
        Long clienteCrmId
) {
}
