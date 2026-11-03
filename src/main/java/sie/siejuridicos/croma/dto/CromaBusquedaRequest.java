package sie.siejuridicos.croma.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CromaBusquedaRequest(
        @NotBlank(message = "El nombre o razón social no puede estar vacío")
        @Size(min = 3, max = 200, message = "El nombre debe tener entre 3 y 200 caracteres")
        String nombre,

        // 'natural' o 'juridical'
        String tipoEntidad,

        Boolean soloActivos
) {
    public String tipoEntidadNormalizado() {
        if (tipoEntidad == null || tipoEntidad.isBlank()) {
            return "natural";
        }
        String t = tipoEntidad.strip().toLowerCase();
        return t.equals("juridical") || t.equals("juridica") ? "juridical" : "natural";
    }

    public boolean esSoloActivos() {
        return Boolean.TRUE.equals(soloActivos);
    }
}
