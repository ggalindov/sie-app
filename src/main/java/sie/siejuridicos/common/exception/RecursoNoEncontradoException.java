package sie.siejuridicos.common.exception;

import org.springframework.http.HttpStatus;

public class RecursoNoEncontradoException extends ApiException {

    public RecursoNoEncontradoException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
