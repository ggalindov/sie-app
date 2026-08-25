package sie.siejuridicos.marketing.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Límites alineados con suscriptores_marketing.nombre/correo VARCHAR(150) (V12).
public record SuscribirNewsletterRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 150, message = "El nombre no puede superar los 150 caracteres")
        String nombre,

        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no tiene un formato válido")
        @Size(max = 150, message = "El correo no puede superar los 150 caracteres")
        String correo,

        // Honeypot (ver CampoTrampa): un humano real nunca llena este campo porque no lo ve.
        String sitioWeb
) {
}
