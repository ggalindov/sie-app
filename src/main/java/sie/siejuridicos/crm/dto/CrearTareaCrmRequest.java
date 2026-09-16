package sie.siejuridicos.crm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import sie.siejuridicos.crm.PrioridadTareaCrm;

import java.time.LocalDateTime;

public record CrearTareaCrmRequest(
        @NotBlank(message = "El título de la tarea es obligatorio")
        @Size(max = 255, message = "El título no puede superar 255 caracteres")
        String titulo,

        @Size(max = 4000, message = "La descripción no puede superar 4000 caracteres")
        String descripcion,

        LocalDateTime fechaVencimiento,

        PrioridadTareaCrm prioridad,

        @Size(max = 150)
        String usuarioNombre
) {
}
