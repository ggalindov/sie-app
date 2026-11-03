package sie.siejuridicos.croma;

import org.springframework.http.HttpStatus;
import sie.siejuridicos.common.exception.ApiException;

public class CuotaCromaAgotadaException extends ApiException {
    public CuotaCromaAgotadaException(String mensaje) {
        super(HttpStatus.TOO_MANY_REQUESTS, mensaje);
    }
}
