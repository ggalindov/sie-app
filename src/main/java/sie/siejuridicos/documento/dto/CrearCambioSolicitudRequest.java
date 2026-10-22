package sie.siejuridicos.documento.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CrearCambioSolicitudRequest(
        @NotNull(message = "Falta indicar el documento.") UUID documentoId,
        @NotBlank(message = "Falta el tipo de acción.") String tipoAccion,
        @NotBlank(message = "Debes justificar la solicitud.") String justificacion,
        String payloadJson
) {
}
