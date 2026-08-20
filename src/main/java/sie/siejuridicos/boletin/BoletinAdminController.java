package sie.siejuridicos.boletin;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sie.siejuridicos.boletin.dto.BoletinEnviadoResponse;

import java.util.List;

// Solo lectura: el envío es automático (ver ArticuloService.notificarPublicacion), no hay
// composición manual. Mismo alcance que /api/admin/marketing en SecurityConfig, solo
// ADMIN_GENERAL.
@RestController
@RequestMapping("/api/admin/boletines")
@PreAuthorize("hasRole('ADMIN_GENERAL')")
public class BoletinAdminController {

    private final BoletinService boletinService;

    public BoletinAdminController(BoletinService boletinService) {
        this.boletinService = boletinService;
    }

    @GetMapping
    public ResponseEntity<List<BoletinEnviadoResponse>> listar() {
        return ResponseEntity.ok(boletinService.listar());
    }
}
