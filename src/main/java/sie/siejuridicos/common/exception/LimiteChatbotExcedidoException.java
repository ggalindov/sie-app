package sie.siejuridicos.common.exception;

import org.springframework.http.HttpStatus;

public class LimiteChatbotExcedidoException extends ApiException {

    public LimiteChatbotExcedidoException(String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, message);
    }
}
