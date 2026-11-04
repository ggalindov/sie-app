package sie.siejuridicos.caso.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CrearCasoRequest(
        @NotBlank @Size(max = 150) String nombreCliente,
        @NotBlank @Email @Size(max = 150) String correoCliente,
        @Size(max = 30) String telefonoCliente,
        @NotBlank @Size(max = 50) @Pattern(regexp = "^[A-Za-z0-9-]+$", message = "El radicado solo puede tener letras, números y guiones")
        String radicadoId,
        @Size(max = 2000) String notasInternas,
        Boolean esPrivadoCifrado,
        String motivoConfidencialidad
) {
    public CrearCasoRequest(String nombreCliente, String correoCliente, String telefonoCliente, String radicadoId, String notasInternas) {
        this(nombreCliente, correoCliente, telefonoCliente, radicadoId, notasInternas, false, null);
    }

    public boolean esPrivado() {
        return Boolean.TRUE.equals(esPrivadoCifrado);
    }
}
