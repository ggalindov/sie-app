package sie.siejuridicos.croma.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CromaBusquedaRequest(
        @NotBlank(message = "El nombre, cédula o razón social no puede estar vacío")
        @Size(min = 3, max = 200, message = "La búsqueda debe tener entre 3 y 200 caracteres")
        String nombre,

        // 'natural' o 'juridical'
        String tipoEntidad,

        Boolean soloActivos,

        // Opcional: corporación específica para SAMAI (ej. '1100103' para Consejo de Estado)
        String corporacionSamai,

        // Opcional: filtro de rama ('TODAS', 'ORDINARIA', 'ADMINISTRATIVA', 'CONSTITUCIONAL', 'SIE')
        String ramaFiltro,

        // Opcional: documento explícito
        String documento
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
