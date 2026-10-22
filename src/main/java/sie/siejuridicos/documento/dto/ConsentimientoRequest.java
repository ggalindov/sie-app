package sie.siejuridicos.documento.dto;

import jakarta.validation.constraints.NotBlank;

public record ConsentimientoRequest(
        @NotBlank(message = "Falta el texto de consentimiento aceptado.") String textoAceptado,
        boolean aceptar
) {
}
