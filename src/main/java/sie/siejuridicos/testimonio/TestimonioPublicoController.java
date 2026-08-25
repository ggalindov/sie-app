package sie.siejuridicos.testimonio;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sie.siejuridicos.common.seguridad.CampoTrampa;
import sie.siejuridicos.testimonio.dto.CrearTestimonioRequest;
import sie.siejuridicos.testimonio.dto.TestimonioResponse;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/testimonios")
public class TestimonioPublicoController {

    private final TestimonioService testimonioService;

    public TestimonioPublicoController(TestimonioService testimonioService) {
        this.testimonioService = testimonioService;
    }

    @PostMapping
    public ResponseEntity<TestimonioResponse> crear(@Valid @RequestBody CrearTestimonioRequest request) {
        if (CampoTrampa.esBot(request.sitioWeb())) {
            return ResponseEntity.status(HttpStatus.CREATED).body(new TestimonioResponse(
                    0L, request.nombre(), request.empresa(), request.cargo(), request.cita(),
                    request.calificacion(), LocalDateTime.now()
            ));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(testimonioService.crear(request));
    }

    @GetMapping
    public ResponseEntity<List<TestimonioResponse>> listarAprobados() {
        return ResponseEntity.ok(testimonioService.listarAprobados());
    }
}
