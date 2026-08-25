package sie.siejuridicos.solicitud;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sie.siejuridicos.common.seguridad.CampoTrampa;
import sie.siejuridicos.solicitud.dto.CrearSolicitudRequest;
import sie.siejuridicos.solicitud.dto.SolicitudResponse;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/solicitudes")
public class SolicitudPublicoController {

    private final SolicitudService solicitudService;

    public SolicitudPublicoController(SolicitudService solicitudService) {
        this.solicitudService = solicitudService;
    }

    @PostMapping
    public ResponseEntity<SolicitudResponse> crear(@Valid @RequestBody CrearSolicitudRequest request) {
        if (CampoTrampa.esBot(request.sitioWeb())) {
            return ResponseEntity.status(HttpStatus.CREATED).body(new SolicitudResponse(
                    0L, request.nombre(), request.correo(), request.telefono(), request.mensaje(),
                    OrigenSolicitud.FORMULARIO, EstadoSolicitud.NUEVO, null, LocalDateTime.now(), null, null
            ));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(solicitudService.crear(request));
    }
}
