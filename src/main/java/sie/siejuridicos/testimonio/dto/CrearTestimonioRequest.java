package sie.siejuridicos.testimonio.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CrearTestimonioRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 150, message = "El nombre es demasiado largo")
        String nombre,

        @Size(max = 150, message = "El nombre de la empresa es demasiado largo")
        String empresa,

        @Size(max = 150, message = "El cargo es demasiado largo")
        String cargo,

        @NotBlank(message = "El testimonio es obligatorio")
        @Size(max = 600, message = "El testimonio no puede superar los 600 caracteres")
        String cita,

        @NotNull(message = "La calificación es obligatoria")
        @Min(value = 1, message = "La calificación mínima es 1")
        @Max(value = 5, message = "La calificación máxima es 5")
        Integer calificacion,

        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no tiene un formato válido")
        String correo
) {
}
