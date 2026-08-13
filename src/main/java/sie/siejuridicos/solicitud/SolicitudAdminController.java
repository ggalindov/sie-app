package sie.siejuridicos.solicitud;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sie.siejuridicos.solicitud.dto.ActualizarEstadoSolicitudRequest;
import sie.siejuridicos.solicitud.dto.AgendarCitaRequest;
import sie.siejuridicos.solicitud.dto.SolicitudResponse;

import java.time.LocalDate;
import java.util.List;

// Reglas de rol reforzadas también en SecurityConfig (/api/admin/**); @PreAuthorize
// queda como segunda capa de defensa a nivel de método.
@RestController
@RequestMapping("/api/admin/solicitudes")
@PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ABOGADO')")
public class SolicitudAdminController {

    private final SolicitudService solicitudService;

    public SolicitudAdminController(SolicitudService solicitudService) {
        this.solicitudService = solicitudService;
    }

    @GetMapping
    public ResponseEntity<List<SolicitudResponse>> listar(
            @RequestParam(required = false) EstadoSolicitud estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(solicitudService.listar(estado, desde, hasta));
    }

    @PatchMapping("/{id}/estado")
    public ResponseEntity<SolicitudResponse> actualizarEstado(@PathVariable Long id,
                                                                @Valid @RequestBody ActualizarEstadoSolicitudRequest request) {
        return ResponseEntity.ok(solicitudService.actualizarEstado(id, request.nuevoEstado()));
    }

    @PatchMapping("/{id}/cita")
    public ResponseEntity<SolicitudResponse> agendarCita(@PathVariable Long id,
                                                           @Valid @RequestBody AgendarCitaRequest request) {
        return ResponseEntity.ok(solicitudService.agendarCita(id, request.fechaHora()));
    }
}
