package sie.siejuridicos.faq;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sie.siejuridicos.faq.dto.PreguntaFrecuenteResponse;

import java.util.List;

@RestController
@RequestMapping("/api/faq")
public class PreguntaFrecuentePublicoController {

    private final PreguntaFrecuenteService preguntaFrecuenteService;

    public PreguntaFrecuentePublicoController(PreguntaFrecuenteService preguntaFrecuenteService) {
        this.preguntaFrecuenteService = preguntaFrecuenteService;
    }

    @GetMapping
    public ResponseEntity<List<PreguntaFrecuenteResponse>> listar() {
        return ResponseEntity.ok(preguntaFrecuenteService.listarAprobadas());
    }
}
