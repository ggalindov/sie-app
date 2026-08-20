package sie.siejuridicos.solicitud.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Los límites de tamaño reflejan las columnas de la tabla solicitudes (V4): nombre/correo
// VARCHAR(150), telefono VARCHAR(30). Sin ellos, un valor demasiado largo llega hasta
// fn_crear_solicitud y Postgres lo rechaza con "value too long", que no tiene un ERRCODE
// propio (SIE0x) y termina como un 500 genérico en vez de un 400 de validación claro.
public record CrearSolicitudRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 150, message = "El nombre no puede superar los 150 caracteres")
        String nombre,

        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no tiene un formato válido")
        @Size(max = 150, message = "El correo no puede superar los 150 caracteres")
        String correo,

        @Size(max = 30, message = "El teléfono no puede superar los 30 caracteres")
        String telefono,

        @NotBlank(message = "El mensaje es obligatorio")
        @Size(max = 5000, message = "El mensaje no puede superar los 5000 caracteres")
        String mensaje,

        // RF-19: no se permite enviar el formulario sin aceptar el aviso de tratamiento de datos (Habeas Data)
        @AssertTrue(message = "Debe aceptar el aviso de tratamiento de datos personales")
        boolean aceptaTratamientoDatos,

        // Consentimiento independiente y explícito para marketing (no implícito en aceptaTratamientoDatos)
        boolean aceptaMarketing
) {
}
