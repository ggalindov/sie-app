package sie.siejuridicos.caso.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CrearCasoRequest(
        @NotBlank @Size(max = 150) String nombreCliente,
        @NotBlank @Email @Size(max = 150) String correoCliente,
        @Size(max = 30) String telefonoCliente,
        // El radicado judicial REAL de la firma (columna I de su Google Sheets), no un código
        // generado por nosotros. El patrón es deliberadamente permisivo (letras, números,
        // guiones): no se conoce un formato único y estricto para todos los despachos, solo
        // se bloquean caracteres que no tendrían por qué aparecer en un radicado real.
        @NotBlank @Size(max = 50) @Pattern(regexp = "^[A-Za-z0-9-]+$", message = "El radicado solo puede tener letras, números y guiones")
        String radicadoId,
        @Size(max = 2000) String notasInternas
) {
}
