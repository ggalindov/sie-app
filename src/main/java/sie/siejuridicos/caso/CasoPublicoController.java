package sie.siejuridicos.caso;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sie.siejuridicos.caso.dto.CasoConsultaResponse;
import sie.siejuridicos.common.exception.RecursoNoEncontradoException;
import sie.siejuridicos.security.ConsultaCasoAbuseGuard;

@RestController
@RequestMapping("/api/casos")
public class CasoPublicoController {

    private final CasoService casoService;
    private final ConsultaCasoAbuseGuard abuseGuard;

    public CasoPublicoController(CasoService casoService, ConsultaCasoAbuseGuard abuseGuard) {
        this.casoService = casoService;
        this.abuseGuard = abuseGuard;
    }

    // getRemoteAddr(), no un header leído a mano: en producción, Tomcat ya lo corrige con
    // el X-Forwarded-For real SOLO si la conexión TCP viene de Caddy (ver
    // server.tomcat.remoteip.trusted-proxies en application-prod.properties), el mismo
    // mecanismo del que depende RateLimitFilter.
    @GetMapping("/consulta")
    public ResponseEntity<CasoConsultaResponse> consultar(@RequestParam String codigo, HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        abuseGuard.verificarNoBloqueado(ip);
        try {
            CasoConsultaResponse respuesta = casoService.consultar(codigo);
            abuseGuard.registrarExito(ip);
            return ResponseEntity.ok(respuesta);
        } catch (RecursoNoEncontradoException ex) {
            abuseGuard.registrarFallo(ip);
            throw ex;
        }
    }
}
