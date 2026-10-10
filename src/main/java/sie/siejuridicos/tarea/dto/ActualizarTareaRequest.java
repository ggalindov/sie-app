package sie.siejuridicos.tarea.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import sie.siejuridicos.tarea.PrioridadTarea;

import java.time.LocalDateTime;

// Edición completa de una tarea existente (título, descripción, vencimiento, prioridad y
// reasignación de responsable) -- separado de completar()/reabrir(), que son una acción de un
// solo campo y no necesitan repetir todo el resto.
public record ActualizarTareaRequest(
        @NotBlank(message = "El título de la tarea es obligatorio")
        @Size(max = 255, message = "El título no puede superar 255 caracteres")
        String titulo,

        @Size(max = 4000, message = "La descripción no puede superar 4000 caracteres")
        String descripcion,

        LocalDateTime fechaVencimiento,

        PrioridadTarea prioridad,

        Long usuarioAsignadoId
) {
}
