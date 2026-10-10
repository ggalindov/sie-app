package sie.siejuridicos.tarea.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import sie.siejuridicos.tarea.PrioridadTarea;

import java.time.LocalDateTime;

public record CrearTareaRequest(
        @NotBlank(message = "El título de la tarea es obligatorio")
        @Size(max = 255, message = "El título no puede superar 255 caracteres")
        String titulo,

        @Size(max = 4000, message = "La descripción no puede superar 4000 caracteres")
        String descripcion,

        LocalDateTime fechaVencimiento,

        PrioridadTarea prioridad,

        @NotNull(message = "Debes asignar un responsable")
        Long usuarioAsignadoId
) {
}
