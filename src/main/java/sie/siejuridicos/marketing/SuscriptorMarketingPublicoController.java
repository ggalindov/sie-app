package sie.siejuridicos.marketing;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sie.siejuridicos.marketing.dto.SuscribirNewsletterRequest;

// Suscripción directa al boletín (sección "Newsletter" de la home), distinta
// del checkbox "aceptaMarketing" dentro del formulario de Agendar asesoría:
// mismo destino (suscriptores_marketing), dos puertas de entrada distintas.
@RestController
@RequestMapping("/api/marketing/suscriptores")
public class SuscriptorMarketingPublicoController {

    private final SuscriptorMarketingService suscriptorMarketingService;

    public SuscriptorMarketingPublicoController(SuscriptorMarketingService suscriptorMarketingService) {
        this.suscriptorMarketingService = suscriptorMarketingService;
    }

    @PostMapping
    public ResponseEntity<Void> suscribir(@Valid @RequestBody SuscribirNewsletterRequest request) {
        suscriptorMarketingService.suscribir(request.nombre(), request.correo());
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
