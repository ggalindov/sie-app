package sie.siejuridicos.crm.dto;

import jakarta.validation.constraints.Size;
import sie.siejuridicos.crm.TipoClienteCrm;

public record ConvertirProspectoRequest(
        TipoClienteCrm tipo,

        @Size(max = 50, message = "La identificación no puede superar 50 caracteres")
        String cedulaNit,

        @Size(max = 255, message = "La dirección no puede superar 255 caracteres")
        String direccion,

        @Size(max = 100, message = "La ciudad no puede superar 100 caracteres")
        String ciudad,

        @Size(max = 50, message = "La etiqueta no puede superar 50 caracteres")
        String etiqueta,

        @Size(max = 2000, message = "Las notas no pueden superar 2000 caracteres")
        String notas
) {
}
