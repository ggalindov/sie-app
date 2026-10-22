package sie.siejuridicos.documento;

import org.springframework.http.HttpStatus;
import sie.siejuridicos.common.exception.ApiException;

public class OtpExpiradoException extends ApiException {

    public OtpExpiradoException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
