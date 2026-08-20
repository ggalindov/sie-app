package sie.siejuridicos.caso.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CrearCasoRequest(
        @NotBlank @Size(max = 150) String nombreCliente,
        @NotBlank @Email @Size(max = 150) String correoCliente,
        @Size(max = 30) String telefonoCliente,
        @NotNull Long idCategoria,
        @Size(max = 2000) String notasInternas
) {
}
