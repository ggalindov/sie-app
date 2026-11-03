package sie.siejuridicos.croma;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sie.siejuridicos.croma.dto.CromaBusquedaRequest;
import sie.siejuridicos.croma.dto.CromaBusquedaResponse;
import sie.siejuridicos.croma.dto.CromaEstadoCuotaResponse;
import sie.siejuridicos.croma.dto.CromaLegalProRequest;
import sie.siejuridicos.croma.dto.CromaLegalProResponse;
import sie.siejuridicos.croma.dto.CromaMemoriaResumenDto;

import java.util.List;

// Controlador administrativo para búsquedas legales y análisis profundo Legal-Pro en Croma.
// Incluye el Orquestador Multi-Key y la Memoria de 7 Días para informes Legal-Pro.
@RestController
@RequestMapping("/api/admin/croma")
@PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ABOGADO')")
public class CromaAdminController {

    private final CromaClient cromaClient;
    private final CromaCreditosService creditosService;
    private final CromaLegalProService legalProService;

    public CromaAdminController(
            CromaClient cromaClient,
            CromaCreditosService creditosService,
            CromaLegalProService legalProService) {
        this.cromaClient = cromaClient;
        this.creditosService = creditosService;
        this.legalProService = legalProService;
    }

    @GetMapping("/cuota")
    public ResponseEntity<CromaEstadoCuotaResponse> obtenerCuota() {
        return ResponseEntity.ok(creditosService.obtenerEstadoCuota());
    }

    @PostMapping("/buscar")
    public ResponseEntity<CromaBusquedaResponse> buscarPorEntidad(@Valid @RequestBody CromaBusquedaRequest request) {
        return ResponseEntity.ok(cromaClient.buscarProcesosPorEntidad(request));
    }

    @PostMapping("/legal-pro")
    public ResponseEntity<CromaLegalProResponse> generarInformeLegalPro(@RequestBody CromaLegalProRequest request) {
        return ResponseEntity.ok(legalProService.generarInformeProfundo(request));
    }

    @GetMapping("/legal-pro/memorias")
    public ResponseEntity<List<CromaMemoriaResumenDto>> listarMemoriasActivas() {
        return ResponseEntity.ok(legalProService.listarMemoriasActivas());
    }

    @GetMapping("/legal-pro/memoria")
    public ResponseEntity<CromaLegalProResponse> obtenerMemoriaCaso(
            @RequestParam(required = false) Long casoId,
            @RequestParam(required = false) String radicado) {
        return legalProService.obtenerDesdeMemoria(casoId, radicado)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}
