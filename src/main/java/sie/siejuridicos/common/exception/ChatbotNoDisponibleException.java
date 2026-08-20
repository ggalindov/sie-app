package sie.siejuridicos.common.exception;

import org.springframework.http.HttpStatus;

// Se lanza cuando el chatbot no puede responder por una causa operativa (falta la API key
// de Anthropic, o la llamada al modelo falló por red/cuota/autenticación), nunca por un
// error de datos del usuario. 503: el problema es del servicio, no de la solicitud.
public class ChatbotNoDisponibleException extends ApiException {

    public ChatbotNoDisponibleException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
