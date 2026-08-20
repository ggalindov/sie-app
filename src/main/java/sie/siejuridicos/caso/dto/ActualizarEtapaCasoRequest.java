package sie.siejuridicos.caso.dto;

import jakarta.validation.constraints.NotNull;
import sie.siejuridicos.caso.EtapaCaso;

public record ActualizarEtapaCasoRequest(
        @NotNull EtapaCaso nuevaEtapa
) {
}
