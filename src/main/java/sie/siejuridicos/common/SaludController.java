package sie.siejuridicos.common;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// Healthcheck minimalista y a mano (no Actuator completo: se evaluó y se descartó a
// propósito por superficie de ataque, ver CLAUDE.md). Deliberadamente NO toca la base de
// datos ni ningún otro sistema externo: solo confirma que la app está viva y respondiendo,
// para que un balanceador o un monitor de uptime externo pueda usarlo sin más lógica.
@RestController
public class SaludController {

    @GetMapping("/api/salud")
    public ResponseEntity<Map<String, String>> salud() {
        return ResponseEntity.ok(Map.of("estado", "OK"));
    }
}
