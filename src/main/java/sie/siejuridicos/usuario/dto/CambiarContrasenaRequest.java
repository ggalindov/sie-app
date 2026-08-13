package sie.siejuridicos.usuario.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CambiarContrasenaRequest(
        @NotBlank(message = "La contraseña actual es obligatoria")
        String contrasenaActual,

        @NotBlank(message = "La nueva contraseña es obligatoria")
        @Size(min = 8, message = "La nueva contraseña debe tener al menos 8 caracteres")
        String contrasenaNueva
) {
}
