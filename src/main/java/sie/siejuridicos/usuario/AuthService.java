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
import sie.siejuridicos.security.LoginAttemptService;
import sie.siejuridicos.usuario.dto.CambiarContrasenaRequest;
import sie.siejuridicos.usuario.dto.LoginRequest;
import sie.siejuridicos.usuario.dto.LoginResponse;

import java.util.Optional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String CREDENCIALES_INVALIDAS = "Correo o contraseña incorrectos";

    // Hash BCrypt "de relleno" sin usuario real detrás. Se compara contra él cuando el
    // correo no existe, solo para que passwordEncoder.matches() consuma un tiempo de CPU
    // similar al de una verificación real: sin esto, un correo inexistente respondía
    // notablemente más rápido que uno existente con contraseña incorrecta (no hay hash que
    // calcular), un canal lateral de tiempo clásico para enumerar qué correos existen.
    private static final String HASH_RELLENO =
            "$2a$10$7EqJtq98hPqEX7fNZaFWoOa2Ihb0X6NAF1uk1BQVfXAAHo9jNs8Wq";

    private final UsuarioInternoRepository usuarioInternoRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;

    public AuthService(UsuarioInternoRepository usuarioInternoRepository,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService,
                        LoginAttemptService loginAttemptService) {
        this.usuarioInternoRepository = usuarioInternoRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginAttemptService = loginAttemptService;
    }

    public LoginResponse login(LoginRequest request) {
        // Antes de tocar la base de datos o calcular ningún hash: si el correo ya está
        // bloqueado por intentos fallidos previos (ver LoginAttemptService), se corta aquí.
        loginAttemptService.verificarNoBloqueado(request.correo());

        Optional<UsuarioInterno> usuarioOpt = usuarioInternoRepository.findByCorreo(request.correo());

        if (usuarioOpt.isEmpty()) {
            log.warn("Intento de acceso fallido: no existe el correo {}", request.correo());
            passwordEncoder.matches(request.contrasena(), HASH_RELLENO);
            loginAttemptService.registrarFallo(request.correo());
            throw new BadCredentialsException(CREDENCIALES_INVALIDAS);
        }
        UsuarioInterno usuario = usuarioOpt.get();

        if (!passwordEncoder.matches(request.contrasena(), usuario.getContrasena())) {
            log.warn("Intento de acceso fallido: contraseña incorrecta para {}", request.correo());
            loginAttemptService.registrarFallo(request.correo());
            throw new BadCredentialsException(CREDENCIALES_INVALIDAS);
        }

        // La verificación de cuenta activa va después de validar la contraseña (y no antes,
        // como estaba) para no revelar mediante un mensaje distinto ("cuenta desactivada"
        // vs "credenciales inválidas") si una contraseña adivinada era o no correcta para
        // una cuenta que resulta estar inactiva.
        if (!usuario.isActivo()) {
            log.warn("Intento de acceso a cuenta desactivada: {}", request.correo());
            throw new DisabledException("Esta cuenta se encuentra desactivada. Contacte al administrador general");
        }

        loginAttemptService.registrarExito(request.correo());
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
