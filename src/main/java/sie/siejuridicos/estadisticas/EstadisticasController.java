package sie.siejuridicos.estadisticas;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sie.siejuridicos.estadisticas.dto.EstadisticasResponse;

// Pedido explícito del usuario (2026-11-04): "para el rol de abogado elimina estadisticas"
// -- queda exclusivo de ADMIN_GENERAL. Regla reforzada también en SecurityConfig
// (/api/admin/estadisticas/**), @PreAuthorize queda como segunda capa de defensa a nivel
// de método.
@RestController
@RequestMapping("/api/admin/estadisticas")
@PreAuthorize("hasRole('ADMIN_GENERAL')")
public class EstadisticasController {

    private final EstadisticasService estadisticasService;

    public EstadisticasController(EstadisticasService estadisticasService) {
        this.estadisticasService = estadisticasService;
    }

    @GetMapping
    public ResponseEntity<EstadisticasResponse> obtener() {
        return ResponseEntity.ok(estadisticasService.obtener());
    }
}
