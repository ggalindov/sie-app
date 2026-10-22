package sie.siejuridicos.documento;

import org.springframework.http.HttpStatus;
import sie.siejuridicos.common.exception.ApiException;

// El número de documento (cédula/NIT) ingresado por quien abrió el link de firma no
// coincide con el registrado para esa parte -- segundo factor de identificación exigido
// antes de enviar el código OTP.
public class IdentidadInvalidaException extends ApiException {

    public IdentidadInvalidaException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
