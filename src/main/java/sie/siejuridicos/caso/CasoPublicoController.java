package sie.siejuridicos.caso;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sie.siejuridicos.caso.dto.CasoConsultaResponse;

@RestController
@RequestMapping("/api/casos")
public class CasoPublicoController {

    private final CasoService casoService;

    public CasoPublicoController(CasoService casoService) {
        this.casoService = casoService;
    }

    @GetMapping("/consulta")
    public ResponseEntity<CasoConsultaResponse> consultar(@RequestParam String codigo) {
        return ResponseEntity.ok(casoService.consultar(codigo));
    }
}
