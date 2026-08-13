package sie.siejuridicos.testimonio;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sie.siejuridicos.testimonio.dto.ModerarTestimonioRequest;
import sie.siejuridicos.testimonio.dto.TestimonioAdminResponse;

import java.util.List;

// Reglas de rol reforzadas también en SecurityConfig (/api/admin/**); @PreAuthorize
// queda como segunda capa de defensa a nivel de método.
@RestController
@RequestMapping("/api/admin/testimonios")
@PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ABOGADO')")
public class TestimonioAdminController {

    private final TestimonioService testimonioService;

    public TestimonioAdminController(TestimonioService testimonioService) {
        this.testimonioService = testimonioService;
    }

    @GetMapping
    public ResponseEntity<List<TestimonioAdminResponse>> listar() {
        return ResponseEntity.ok(testimonioService.listarTodos());
    }

    @PatchMapping("/{id}")
    public ResponseEntity<TestimonioAdminResponse> moderar(
            @PathVariable Long id,
            @Valid @RequestBody ModerarTestimonioRequest request
    ) {
        return ResponseEntity.ok(testimonioService.moderar(id, request.nuevoEstado()));
    }
}
