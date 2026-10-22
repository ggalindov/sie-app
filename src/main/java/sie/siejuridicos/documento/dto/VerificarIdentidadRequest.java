package sie.siejuridicos.documento.dto;

import jakarta.validation.constraints.NotBlank;

// Número de cédula o NIT tal como el firmante lo escribe (con o sin puntos/guiones; se
// normaliza antes de comparar, ver PublicSignatureService#verificarIdentidad).
public record VerificarIdentidadRequest(@NotBlank(message = "Debes ingresar tu número de documento.") String numeroDocumento) {
}
