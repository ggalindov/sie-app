package sie.siejuridicos.documento;

import org.springframework.http.HttpStatus;
import sie.siejuridicos.common.exception.ApiException;

// Se lanza al intentar modificar un documento cuyo estadoDocumento ya alcanzó SELLADO (o
// posterior): a partir de ese punto el contenido es inmutable y cualquier cambio real debe
// pasar por una DocumentoCambioSolicitud con doble confirmación (ADMIN_GENERAL + ABOGADO).
public class DocumentoSelladoException extends ApiException {

    public DocumentoSelladoException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
