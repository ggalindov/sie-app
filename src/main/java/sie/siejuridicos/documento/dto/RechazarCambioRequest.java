package sie.siejuridicos.documento.dto;

import jakarta.validation.constraints.NotBlank;

public record RechazarCambioRequest(@NotBlank(message = "Debes indicar el motivo del rechazo.") String motivo) {
}
