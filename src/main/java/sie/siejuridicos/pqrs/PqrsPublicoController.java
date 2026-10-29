package sie.siejuridicos.pqrs;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sie.siejuridicos.common.exception.RecursoNoEncontradoException;
import sie.siejuridicos.common.seguridad.CampoTrampa;
import sie.siejuridicos.pqrs.dto.CrearPqrsRequest;
import sie.siejuridicos.pqrs.dto.PqrsConsultaResponse;
import sie.siejuridicos.pqrs.dto.PqrsCreadaResponse;
import sie.siejuridicos.security.PqrsConsultaAbuseGuard;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/pqrs")
public class PqrsPublicoController {

    private final PqrsService pqrsService;
    private final PqrsConsultaAbuseGuard abuseGuard;

    public PqrsPublicoController(PqrsService pqrsService, PqrsConsultaAbuseGuard abuseGuard) {
        this.pqrsService = pqrsService;
        this.abuseGuard = abuseGuard;
    }

    @PostMapping
    public ResponseEntity<PqrsCreadaResponse> crear(@Valid @RequestBody CrearPqrsRequest request) {
        if (CampoTrampa.esBot(request.sitioWeb())) {
            // Mismo patrón que TestimonioPublicoController: un 201 fabricado, sin tocar la
            // base de datos ni disparar correos, para no delatarle al bot que su envío fue
            // descartado.
            LocalDateTime ahora = LocalDateTime.now();
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new PqrsCreadaResponse("PQRS-000000", ahora, ahora.plusDays(15)));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(pqrsService.crear(request));
    }

    // getRemoteAddr(), no un header leído a mano: mismo motivo que CasoPublicoController
    // (Tomcat ya lo corrige con el X-Forwarded-For real solo si la conexión viene de Caddy).
    @GetMapping("/consulta")
    public ResponseEntity<PqrsConsultaResponse> consultar(@RequestParam String radicado, HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        abuseGuard.verificarNoBloqueado(ip);
        try {
            PqrsConsultaResponse respuesta = pqrsService.consultarPorRadicado(radicado);
            abuseGuard.registrarExito(ip);
            return ResponseEntity.ok(respuesta);
        } catch (RecursoNoEncontradoException ex) {
            abuseGuard.registrarFallo(ip);
            throw ex;
        }
    }
}
