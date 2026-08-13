package sie.siejuridicos.common.exception;

import org.springframework.http.HttpStatus;

public class EntidadInvalidaException extends ApiException {

    public EntidadInvalidaException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
}
