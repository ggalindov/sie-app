package sie.siejuridicos.analitica.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record RegistrarVisitaRequest(
        // Debe ser un UUID v4 generado por crypto.randomUUID() en el navegador (ver
        // VisitorTracker en el frontend). El formato estricto evita que la columna reciba
        // basura o cadenas gigantes de un cliente malicioso.
        @NotBlank
        @Pattern(
                regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                message = "El identificador de visitante no tiene un formato válido"
        )
        String visitanteId
) {
}
