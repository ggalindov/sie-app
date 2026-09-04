package sie.siejuridicos.common.exception;

import org.springframework.http.HttpStatus;

// Se lanza cuando no se puede leer el Google Sheets de casos por una causa operativa
// (credenciales sin configurar, la hoja no está compartida con la cuenta de servicio, la
// llamada a la API de Google falló por red/cuota), nunca por un error de datos del usuario.
// 503: el problema es del servicio, no de la solicitud. Mismo patrón que
// ChatbotNoDisponibleException.
public class HojaCalculoNoDisponibleException extends ApiException {

    public HojaCalculoNoDisponibleException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
