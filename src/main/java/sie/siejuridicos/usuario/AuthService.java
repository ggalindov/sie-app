package sie.siejuridicos.usuario;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sie.siejuridicos.common.exception.RecursoNoEncontradoException;
import sie.siejuridicos.security.JwtService;
import sie.siejuridicos.usuario.dto.CambiarContrasenaRequest;
import sie.siejuridicos.usuario.dto.LoginRequest;
import sie.siejuridicos.usuario.dto.LoginResponse;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String CREDENCIALES_INVALIDAS = "Correo o contraseña incorrectos";

    private final UsuarioInternoRepository usuarioInternoRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UsuarioInternoRepository usuarioInternoRepository,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService) {
        this.usuarioInternoRepository = usuarioInternoRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {
        UsuarioInterno usuario = usuarioInternoRepository.findByCorreo(request.correo())
                .orElseThrow(() -> {
                    log.warn("Intento de acceso fallido: no existe el correo {}", request.correo());
                    return new BadCredentialsException(CREDENCIALES_INVALIDAS);
                });

        if (!usuario.isActivo()) {
            log.warn("Intento de acceso a cuenta desactivada: {}", request.correo());
            throw new DisabledException("Esta cuenta se encuentra desactivada. Contacte al administrador general");
        }

        if (!passwordEncoder.matches(request.contrasena(), usuario.getContrasena())) {
            log.warn("Intento de acceso fallido: contraseña incorrecta para {}", request.correo());
            throw new BadCredentialsException(CREDENCIALES_INVALIDAS);
        }

        String token = jwtService.generarToken(usuario);
        return new LoginResponse(token, usuario.getNombre(), usuario.getCorreo(), usuario.getRol(), jwtService.getExpirationMs());
    }

    @Transactional
    public void cambiarContrasena(Long usuarioId, CambiarContrasenaRequest request) {
        UsuarioInterno usuario = usuarioInternoRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el usuario con id " + usuarioId));

        if (!passwordEncoder.matches(request.contrasenaActual(), usuario.getContrasena())) {
            throw new BadCredentialsException("La contraseña actual no es correcta");
        }

        usuario.setContrasena(passwordEncoder.encode(request.contrasenaNueva()));
        usuarioInternoRepository.save(usuario);
    }
}
