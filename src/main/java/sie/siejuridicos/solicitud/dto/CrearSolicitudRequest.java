package sie.siejuridicos.solicitud.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CrearSolicitudRequest(
        @NotBlank(message = "El nombre es obligatorio")
        String nombre,

        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no tiene un formato válido")
        String correo,

        String telefono,

        @NotBlank(message = "El mensaje es obligatorio")
        String mensaje,

        // RF-19: no se permite enviar el formulario sin aceptar el aviso de tratamiento de datos (Habeas Data)
        @AssertTrue(message = "Debe aceptar el aviso de tratamiento de datos personales")
        boolean aceptaTratamientoDatos,

        // Consentimiento independiente y explícito para marketing (no implícito en aceptaTratamientoDatos)
        boolean aceptaMarketing
) {
}
