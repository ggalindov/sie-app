package sie.siejuridicos.common.exception;

import org.springframework.http.HttpStatus;

public class CuentaBloqueadaException extends ApiException {

    public CuentaBloqueadaException(String message) {
        super(HttpStatus.LOCKED, message);
    }
}
