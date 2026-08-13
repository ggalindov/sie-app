package sie.siejuridicos.common.exception;

import org.springframework.http.HttpStatus;

public class ConflictoNegocioException extends ApiException {

    public ConflictoNegocioException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
