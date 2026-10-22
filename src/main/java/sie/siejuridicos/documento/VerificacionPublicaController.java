package sie.siejuridicos.documento;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import sie.siejuridicos.common.exception.EntidadInvalidaException;
import sie.siejuridicos.documento.dto.VerificacionPublicaResponse;

import java.io.IOException;

// Portal PÚBLICO (sin autenticación) de verificación anti-alteración de documentos
// sellados. El límite de tasa lo pone RateLimitFilter, antes de que la solicitud llegue
// aquí (ver sie.siejuridicos.security), no un mecanismo propio de este controlador.
@RestController
@RequestMapping("/api/v1/verificacion")
public class VerificacionPublicaController {

    private final VerificacionPublicaService servicio;

    public VerificacionPublicaController(VerificacionPublicaService servicio) {
        this.servicio = servicio;
    }

    @GetMapping(value = "/{codigo}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<VerificacionPublicaResponse> porCodigo(@PathVariable String codigo, HttpServletRequest request) {
        String ip = ClientIpResolver.resolver(request);
        VerificacionPublicaResponse respuesta = servicio.verificarPorCodigo(codigo, ip);
        return ResponseEntity.status(respuesta.veredicto() == VerificacionVeredicto.NO_REGISTRADO
                ? HttpStatus.NOT_FOUND : HttpStatus.OK).body(respuesta);
    }

    @PostMapping(value = "/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<VerificacionPublicaResponse> porPdf(@RequestParam("archivo") MultipartFile archivo, HttpServletRequest request)
            throws IOException {
        if (archivo.isEmpty()) {
            throw new EntidadInvalidaException("Adjunta el archivo PDF que deseas verificar.");
        }
        // Pedido/decisión deliberada (igual que el motor original): CUALQUIER archivo,
        // incluido uno que no sea un PDF real, responde 200 con veredicto NO_REGISTRADO en
        // vez de un error -- el servicio solo calcula un hash y busca una coincidencia,
        // nunca ejecuta el contenido. El límite de 25MB (ver application.properties) y el
        // rate-limit propio de esta ruta ya acotan el riesgo de abuso.
        String ip = ClientIpResolver.resolver(request);
        return ResponseEntity.ok(servicio.verificarPdf(archivo.getBytes(), ip));
    }
}
