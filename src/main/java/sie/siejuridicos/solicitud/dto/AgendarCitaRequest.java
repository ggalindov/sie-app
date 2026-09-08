package sie.siejuridicos.solicitud.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import sie.siejuridicos.solicitud.TipoReunion;

import java.time.LocalDateTime;

// correo/telefono llegan como los datos vigentes del cliente en el momento de agendar (el
// formulario los muestra precargados desde la solicitud, pero editables: es el punto natural
// para corregir un dato mal escrito antes de que salga la notificacion). abogadoId es
// obligatorio solo si quien agenda es ADMIN_GENERAL (elige a quien queda asignada la reunion);
// si agenda un ABOGADO, SolicitudService.agendarCita lo ignora y usa siempre al propio
// abogado -- ver ese metodo para el porque.
//
// linkReunion y lugarReunion son mutuamente excluyentes segun tipoReunion, no se puede
// validar eso solo con anotaciones (@NotBlank no admite "obligatorio solo si..."), asi que
// esa parte se valida en SolicitudService.agendarCita -- incluyendo que linkReunion sea de
// verdad un link de Meet o Zoom cuando tipoReunion es VIRTUAL.
//
// Los @Size de aqui abajo reflejan el tamaño real de las columnas de "solicitudes" (V4:
// correo/telefono VARCHAR(150)/VARCHAR(30); V31/V32: link_reunion VARCHAR(500),
// lugar_reunion VARCHAR(300)) -- mismo motivo que CrearSolicitudRequest: sin esto, un valor
// demasiado largo llega hasta el INSERT/UPDATE y Postgres lo rechaza con "value too long",
// que termina como un 503 generico (ver GlobalExceptionHandler) en vez de un 400 de
// validacion claro.
public record AgendarCitaRequest(
        @NotNull(message = "La fecha y hora de la cita son obligatorias")
        @Future(message = "La cita debe programarse en una fecha futura")
        LocalDateTime fechaHora,

        @NotBlank(message = "El correo del cliente es obligatorio")
        @Email(message = "El correo del cliente no es válido")
        @Size(max = 150, message = "El correo no puede superar los 150 caracteres")
        String correo,

        @Size(max = 30, message = "El teléfono no puede superar los 30 caracteres")
        String telefono,

        @NotNull(message = "Indica si la reunión es virtual o presencial")
        TipoReunion tipoReunion,

        @Size(max = 500, message = "El link de la reunión no puede superar los 500 caracteres")
        String linkReunion,

        @Size(max = 300, message = "El lugar de la reunión no puede superar los 300 caracteres")
        String lugarReunion,

        Long abogadoId
) {
}
