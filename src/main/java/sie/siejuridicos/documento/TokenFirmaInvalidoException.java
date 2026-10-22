package sie.siejuridicos.documento;

import org.springframework.http.HttpStatus;
import sie.siejuridicos.common.exception.ApiException;

public class TokenFirmaInvalidoException extends ApiException {

    public TokenFirmaInvalidoException(String message) {
        super(HttpStatus.UNAUTHORIZED, message);
    }
}
