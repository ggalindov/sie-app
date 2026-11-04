package sie.siejuridicos.usuario;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sie.siejuridicos.common.exception.RecursoNoEncontradoException;
import sie.siejuridicos.documento.events.FirmaStaffActualizadaEvent;
import sie.siejuridicos.registro.RegistroSistemaService;
import sie.siejuridicos.registro.TipoRegistroSistema;
import sie.siejuridicos.common.exception.EntidadInvalidaException;
import sie.siejuridicos.security.JwtService;
import sie.siejuridicos.security.LoginAttemptService;
import sie.siejuridicos.security.TurnstileService;
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
    private final RegistroSistemaService registroSistemaService;
    private final ApplicationEventPublisher eventPublisher;
    private final TurnstileService turnstileService;

    public AuthService(UsuarioInternoRepository usuarioInternoRepository,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService,
                        LoginAttemptService loginAttemptService,
                        RegistroSistemaService registroSistemaService,
                        ApplicationEventPublisher eventPublisher,
                        TurnstileService turnstileService) {
        this.usuarioInternoRepository = usuarioInternoRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginAttemptService = loginAttemptService;
        this.registroSistemaService = registroSistemaService;
        this.eventPublisher = eventPublisher;
        this.turnstileService = turnstileService;
    }

    public LoginResponse login(LoginRequest request) {
        // Pedido explícito del usuario: "captcha al login" -- lo primero de todo, antes
        // incluso del bloqueo por intentos fallidos: no tiene sentido gastar esa consulta
        // en memoria si ni siquiera se resolvió el captcha. TurnstileService.verificar()
        // deja pasar sin exigir nada si app.turnstile.secret-key no está configurada (ver
        // ese comentario) -- nunca rompe el login en un entorno sin la clave configurada.
        if (!turnstileService.verificar(request.recaptchaToken())) {
            registroSistemaService.registrar(TipoRegistroSistema.INICIO_SESION,
                    "Intento de acceso fallido (captcha inválido): " + request.correo(), false);
            throw new EntidadInvalidaException("Verifica el captcha e intenta de nuevo.");
        }

        // Antes de tocar la base de datos o calcular ningún hash: si el correo ya está
        // bloqueado por intentos fallidos previos (ver LoginAttemptService), se corta aquí.
        loginAttemptService.verificarNoBloqueado(request.correo());

        Optional<UsuarioInterno> usuarioOpt = usuarioInternoRepository.findByCorreo(request.correo());

        if (usuarioOpt.isEmpty()) {
            log.warn("Intento de acceso fallido: no existe el correo {}", request.correo());
            passwordEncoder.matches(request.contrasena(), HASH_RELLENO);
            loginAttemptService.registrarFallo(request.correo());
            // Solo se registra el correo intentado, nunca la contraseña -- ver CLAUDE.md
            // sección de seguridad y la restricción explícita del usuario sobre este punto.
            registroSistemaService.registrar(TipoRegistroSistema.INICIO_SESION,
                    "Intento de acceso fallido (correo no registrado): " + request.correo(), false);
            throw new BadCredentialsException(CREDENCIALES_INVALIDAS);
        }
        UsuarioInterno usuario = usuarioOpt.get();

        if (!passwordEncoder.matches(request.contrasena(), usuario.getContrasena())) {
            log.warn("Intento de acceso fallido: contraseña incorrecta para {}", request.correo());
            loginAttemptService.registrarFallo(request.correo());
            registroSistemaService.registrar(TipoRegistroSistema.INICIO_SESION,
                    "Intento de acceso fallido (contraseña incorrecta): " + request.correo(), false);
            throw new BadCredentialsException(CREDENCIALES_INVALIDAS);
        }

        // La verificación de cuenta activa va después de validar la contraseña (y no antes,
        // como estaba) para no revelar mediante un mensaje distinto ("cuenta desactivada"
        // vs "credenciales inválidas") si una contraseña adivinada era o no correcta para
        // una cuenta que resulta estar inactiva.
        if (!usuario.isActivo()) {
            log.warn("Intento de acceso a cuenta desactivada: {}", request.correo());
            registroSistemaService.registrar(TipoRegistroSistema.INICIO_SESION,
                    "Intento de acceso a cuenta desactivada: " + request.correo(), false);
            throw new DisabledException("Esta cuenta se encuentra desactivada. Contacte al administrador general");
        }

        loginAttemptService.registrarExito(request.correo());
        registroSistemaService.registrar(TipoRegistroSistema.INICIO_SESION,
                "Inicio de sesión exitoso: " + usuario.getCorreo() + " (" + usuario.getRol() + ")", true);
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

    // Motor de firma electrónica (ver sie.siejuridicos.documento): cada usuario interno
    // sube su propia firma manuscrita una sola vez (o la reemplaza) desde su perfil --
    // "el sistema debe insertar firma del gerente... el sistema ingresa la firma de los
    // abogados", pedido explícito del usuario. Nunca la sube otro en su nombre: mismo
    // criterio de autoservicio que cambiarContrasena().
    // Bug real diagnosticado en vivo (auditoria del motor de firma, 2026-11-04): a diferencia
    // de la firma de un firmante EXTERNO (que SIEMPRE pasa por
    // SignatureImageProcessor.recortarYEscalar antes de guardarse, ver
    // DocumentoFirmaService#registrarFirmaDeParte), la firma de staff se guardaba tal cual se
    // subia desde "Mi perfil" -- si el PNG tenia un formato que la libreria de estampado de
    // PDF (OpenPDF, distinta de javax.imageio) no podia decodificar, CADA documento que ese
    // usuario tuviera que firmar fallaba de forma determinista al sellar. Reprocesar aqui con
    // el mismo recorte/reescalado normaliza siempre a un PNG ARGB estandar escrito por
    // ImageIO, el mismo formato que ya funciona de forma probada para firmantes externos.
    // Mismas dimensiones de lienzo que DocumentoPdfStamper espera para una firma (640x240).
    @Transactional
    public void actualizarFirma(Long usuarioId, byte[] firmaPng, String contentType) {
        UsuarioInterno usuario = usuarioInternoRepository.findById(usuarioId)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el usuario con id " + usuarioId));
        if (!esImagenDecodificable(firmaPng)) {
            throw new EntidadInvalidaException(
                    "La imagen de la firma no se pudo leer. Sube un PNG o JPG valido e intenta de nuevo.");
        }
        byte[] firmaNormalizada = sie.siejuridicos.documento.pdf.SignatureImageProcessor
                .recortarYEscalar(firmaPng, 1.0f, 640, 240);
        usuario.actualizarFirma(firmaNormalizada, "image/png");
        usuarioInternoRepository.save(usuario);
        eventPublisher.publishEvent(new FirmaStaffActualizadaEvent(usuarioId));
    }

    private static boolean esImagenDecodificable(byte[] datos) {
        if (datos == null || datos.length == 0) {
            return false;
        }
        try {
            return javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(datos)) != null;
        } catch (Exception ex) {
            return false;
        }
    }

    @Transactional(readOnly = true)
    public boolean tieneFirma(Long usuarioId) {
        return usuarioInternoRepository.findById(usuarioId)
                .map(u -> u.getFirmaPng() != null)
                .orElse(false);
    }
}
