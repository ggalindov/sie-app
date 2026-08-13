package sie.siejuridicos.usuario;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sie.siejuridicos.security.UsuarioInternoPrincipal;
import sie.siejuridicos.usuario.dto.CambiarContrasenaRequest;
import sie.siejuridicos.usuario.dto.LoginRequest;
import sie.siejuridicos.usuario.dto.LoginResponse;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    // RF-30: cualquier usuario interno autenticado puede cambiar su propia contraseña
    @PatchMapping("/password")
    public ResponseEntity<Void> cambiarContrasena(@AuthenticationPrincipal UsuarioInternoPrincipal principal,
                                                   @Valid @RequestBody CambiarContrasenaRequest request) {
        authService.cambiarContrasena(principal.getId(), request);
        return ResponseEntity.noContent().build();
    }
}
