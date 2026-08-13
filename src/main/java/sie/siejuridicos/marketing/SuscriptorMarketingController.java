package sie.siejuridicos.marketing;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Reglas de rol reforzadas también en SecurityConfig (/api/admin/**); @PreAuthorize
// queda como segunda capa de defensa a nivel de método.
@RestController
@RequestMapping("/api/admin/marketing/suscriptores")
@PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ABOGADO')")
public class SuscriptorMarketingController {

    private final SuscriptorMarketingRepository suscriptorMarketingRepository;

    public SuscriptorMarketingController(SuscriptorMarketingRepository suscriptorMarketingRepository) {
        this.suscriptorMarketingRepository = suscriptorMarketingRepository;
    }

    @GetMapping
    public ResponseEntity<List<SuscriptorMarketingResponse>> listar() {
        return ResponseEntity.ok(suscriptorMarketingRepository.findByActivoTrueOrderByFechaSuscripcionDesc().stream()
                .map(SuscriptorMarketingResponse::desde)
                .toList());
    }
}
