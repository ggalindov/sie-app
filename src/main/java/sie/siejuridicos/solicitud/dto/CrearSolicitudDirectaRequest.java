package sie.siejuridicos.solicitud.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Creación directa de una solicitud desde el panel (ver SolicitudService.crearDirecta),
// para agendar una reunión sin que el cliente haya llenado el formulario público -- un caso
// ya existente en el sistema, o un cliente completamente nuevo. Mismos límites de tamaño que
// CrearSolicitudRequest (reflejan las columnas de la tabla solicitudes, ver V4), sin los
// campos que solo tienen sentido para el formulario público (consentimientos, honeypot).
public record CrearSolicitudDirectaRequest(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 150, message = "El nombre no puede superar los 150 caracteres")
        String nombre,

        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no tiene un formato válido")
        @Size(max = 150, message = "El correo no puede superar los 150 caracteres")
        String correo,

        @Size(max = 30, message = "El teléfono no puede superar los 30 caracteres")
        String telefono,

        @Size(max = 5000, message = "El mensaje no puede superar los 5000 caracteres")
        String mensaje
) {
}
