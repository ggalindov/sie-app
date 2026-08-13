package sie.siejuridicos.usuario;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

// Como no existe registro público, el primer ADMIN_GENERAL se da de alta a partir de
// variables de entorno (ADMIN_BOOTSTRAP_EMAIL / ADMIN_BOOTSTRAP_PASSWORD). Si ya existe
// un usuario con ese correo, o no se configuraron las variables, no hace nada.
@Component
public class AdminBootstrapRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final UsuarioInternoRepository usuarioInternoRepository;
    private final PasswordEncoder passwordEncoder;
    private final String correo;
    private final String contrasena;
    private final String nombre;

    public AdminBootstrapRunner(UsuarioInternoRepository usuarioInternoRepository,
                                 PasswordEncoder passwordEncoder,
                                 @Value("${app.admin-bootstrap.correo:}") String correo,
                                 @Value("${app.admin-bootstrap.contrasena:}") String contrasena,
                                 @Value("${app.admin-bootstrap.nombre:Administrador General}") String nombre) {
        this.usuarioInternoRepository = usuarioInternoRepository;
        this.passwordEncoder = passwordEncoder;
        this.correo = correo;
        this.contrasena = contrasena;
        this.nombre = nombre;
    }

    @Override
    public void run(String... args) {
        if (correo.isBlank() || contrasena.isBlank()) {
            return;
        }
        if (usuarioInternoRepository.existsByCorreo(correo)) {
            return;
        }

        UsuarioInterno admin = new UsuarioInterno();
        admin.setNombre(nombre);
        admin.setCorreo(correo);
        admin.setContrasena(passwordEncoder.encode(contrasena));
        admin.setRol(RolUsuario.ADMIN_GENERAL);
        admin.setActivo(true);
        usuarioInternoRepository.save(admin);

        log.warn("Se creó el administrador general inicial ({}). Cambie la contraseña de arranque cuanto antes.", correo);
    }
}
