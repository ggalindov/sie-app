package sie.siejuridicos.crm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import sie.siejuridicos.crm.TipoActividadCrm;

import java.time.LocalDateTime;

public record CrearActividadCrmRequest(
        Long solicitudId,
        Long casoId,

        @NotNull(message = "El tipo de actividad es obligatorio")
        TipoActividadCrm tipo,

        @NotBlank(message = "El título es obligatorio")
        @Size(max = 255, message = "El título no puede superar 255 caracteres")
        String titulo,

        @Size(max = 4000, message = "La descripción no puede superar 4000 caracteres")
        String descripcion,

        @Size(max = 150)
        String usuarioNombre,

        LocalDateTime fechaActividad
) {
}
