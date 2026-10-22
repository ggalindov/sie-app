package sie.siejuridicos.documento;

import org.springframework.http.HttpStatus;
import sie.siejuridicos.common.exception.ApiException;

public class OtpInvalidoException extends ApiException {

    public OtpInvalidoException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
