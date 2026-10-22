package sie.siejuridicos.usuario;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import sie.siejuridicos.common.exception.EntidadInvalidaException;
import sie.siejuridicos.security.UsuarioInternoPrincipal;
import sie.siejuridicos.usuario.dto.CambiarContrasenaRequest;
import sie.siejuridicos.usuario.dto.LoginRequest;
import sie.siejuridicos.usuario.dto.LoginResponse;

import java.io.IOException;
import java.util.Map;

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

    // Motor de firma electrónica (ver sie.siejuridicos.documento): "Mi firma" en el perfil
    // -- cada usuario interno sube su propia firma manuscrita una sola vez (o la
    // reemplaza), nunca la sube otro en su nombre. Acepta PNG o JPEG hasta 2MB (una firma
    // manuscrita escaneada/fotografiada nunca necesita más).
    @PostMapping(value = "/firma", consumes = "multipart/form-data")
    public ResponseEntity<Void> actualizarFirma(@AuthenticationPrincipal UsuarioInternoPrincipal principal,
                                                 @RequestParam("archivo") MultipartFile archivo) {
        if (archivo.isEmpty()) {
            throw new EntidadInvalidaException("Adjunta la imagen de tu firma.");
        }
        if (archivo.getSize() > 2L * 1024 * 1024) {
            throw new EntidadInvalidaException("La imagen de la firma no puede superar 2MB.");
        }
        String tipo = archivo.getContentType();
        if (tipo == null || !(tipo.equals("image/png") || tipo.equals("image/jpeg"))) {
            throw new EntidadInvalidaException("La firma debe ser una imagen PNG o JPEG.");
        }
        try {
            authService.actualizarFirma(principal.getId(), archivo.getBytes(), tipo);
        } catch (IOException ex) {
            throw new EntidadInvalidaException("No se pudo leer la imagen de la firma.");
        }
        return ResponseEntity.noContent().build();
    }

    // Para que "Mi firma" en el perfil sepa, al cargar la página, si ya hay una firma
    // guardada -- sin esto, cada usuario vería el formulario de carga vacío incluso
    // después de haber subido su firma en una sesión anterior.
    @GetMapping("/firma")
    public ResponseEntity<Map<String, Boolean>> consultarFirma(@AuthenticationPrincipal UsuarioInternoPrincipal principal) {
        return ResponseEntity.ok(Map.of("tieneFirma", authService.tieneFirma(principal.getId())));
    }
}
