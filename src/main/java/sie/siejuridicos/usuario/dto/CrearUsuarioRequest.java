package sie.siejuridicos.usuario.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Este endpoint crea únicamente cuentas de ABOGADO: solo el administrador general puede
// invocarlo, y el rol se asigna en el servicio (no es un campo elegible por el cliente).
public record CrearUsuarioRequest(
        @NotBlank(message = "El nombre es obligatorio")
        String nombre,

        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no tiene un formato válido")
        String correo,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
        String contrasena
) {
}
