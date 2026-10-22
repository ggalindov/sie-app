package sie.siejuridicos.documento.dto;

import jakarta.validation.constraints.NotBlank;

public record OtpVerificarRequest(@NotBlank(message = "Debes ingresar el código recibido.") String codigo) {
}
