package sie.siejuridicos.pqrs.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import sie.siejuridicos.pqrs.TipoPqrs;

public record CrearPqrsRequest(
        @NotNull(message = "El tipo es obligatorio")
        TipoPqrs tipo,

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 150, message = "El nombre es demasiado largo")
        String nombre,

        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no tiene un formato válido")
        String correo,

        @Size(max = 30, message = "El teléfono es demasiado largo")
        String telefono,

        @NotBlank(message = "El asunto es obligatorio")
        @Size(max = 200, message = "El asunto es demasiado largo")
        String asunto,

        @NotBlank(message = "La descripción es obligatoria")
        @Size(max = 3000, message = "La descripción no puede superar los 3000 caracteres")
        String descripcion,

        // Honeypot (ver CampoTrampa): un humano real nunca llena este campo porque no lo ve.
        String sitioWeb
) {
}
