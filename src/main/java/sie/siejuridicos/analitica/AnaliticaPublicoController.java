package sie.siejuridicos.analitica;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sie.siejuridicos.analitica.dto.RegistrarVisitaRequest;

@RestController
@RequestMapping("/api/analitica")
public class AnaliticaPublicoController {

    private final VisitaUnicaRepository visitaUnicaRepository;

    public AnaliticaPublicoController(VisitaUnicaRepository visitaUnicaRepository) {
        this.visitaUnicaRepository = visitaUnicaRepository;
    }

    @PostMapping("/visita")
    public ResponseEntity<Void> registrarVisita(@Valid @RequestBody RegistrarVisitaRequest request) {
        visitaUnicaRepository.registrarVisita(request.visitanteId());
        return ResponseEntity.noContent().build();
    }
}
