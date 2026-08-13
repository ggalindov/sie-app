package sie.siejuridicos.solicitud;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sie.siejuridicos.solicitud.dto.CrearSolicitudRequest;
import sie.siejuridicos.solicitud.dto.SolicitudResponse;

@RestController
@RequestMapping("/api/solicitudes")
public class SolicitudPublicoController {

    private final SolicitudService solicitudService;

    public SolicitudPublicoController(SolicitudService solicitudService) {
        this.solicitudService = solicitudService;
    }

    @PostMapping
    public ResponseEntity<SolicitudResponse> crear(@Valid @RequestBody CrearSolicitudRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(solicitudService.crear(request));
    }
}
