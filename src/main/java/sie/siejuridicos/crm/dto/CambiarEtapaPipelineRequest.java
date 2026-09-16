package sie.siejuridicos.crm.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import sie.siejuridicos.crm.EtapaPipeline;

public record CambiarEtapaPipelineRequest(
        @NotNull(message = "La nueva etapa es obligatoria")
        EtapaPipeline nuevaEtapa,

        @Size(max = 2000, message = "La nota no puede superar 2000 caracteres")
        String nota,

        @Size(max = 150)
        String usuarioNombre
) {
}
