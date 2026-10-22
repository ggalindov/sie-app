package sie.siejuridicos.documento.dto;

import jakarta.validation.constraints.NotBlank;

public record CancelarDocumentoRequest(@NotBlank(message = "Indica el motivo de la cancelación.") String motivo) {
}
