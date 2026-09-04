package sie.siejuridicos.usuario;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sie.siejuridicos.common.exception.ConflictoNegocioException;
import sie.siejuridicos.common.exception.RecursoNoEncontradoException;
import sie.siejuridicos.registro.RegistroSistemaService;
import sie.siejuridicos.registro.TipoRegistroSistema;
import sie.siejuridicos.usuario.dto.CrearUsuarioRequest;
import sie.siejuridicos.usuario.dto.UsuarioResponse;

import java.util.List;

@Service
public class UsuarioInternoService {

    private final UsuarioInternoRepository usuarioInternoRepository;
    private final PasswordEncoder passwordEncoder;
    private final RegistroSistemaService registroSistemaService;

    public UsuarioInternoService(UsuarioInternoRepository usuarioInternoRepository,
                                  PasswordEncoder passwordEncoder,
                                  RegistroSistemaService registroSistemaService) {
        this.usuarioInternoRepository = usuarioInternoRepository;
        this.passwordEncoder = passwordEncoder;
        this.registroSistemaService = registroSistemaService;
    }

    @Transactional
    public UsuarioResponse crearAbogado(CrearUsuarioRequest request) {
        if (usuarioInternoRepository.existsByCorreo(request.correo())) {
            throw new ConflictoNegocioException("Ya existe un usuario interno con el correo " + request.correo());
        }

        UsuarioInterno usuario = new UsuarioInterno();
        usuario.setNombre(request.nombre());
        usuario.setCorreo(request.correo());
        usuario.setContrasena(passwordEncoder.encode(request.contrasena()));
        usuario.setRol(RolUsuario.ABOGADO);
        usuario.setActivo(true);

        UsuarioResponse creado = UsuarioResponse.desde(usuarioInternoRepository.save(usuario));
        registroSistemaService.registrar(TipoRegistroSistema.USUARIO_CREADO,
                "Usuario creado: " + creado.correo() + " (ABOGADO)", true);
        return creado;
    }

    public List<UsuarioResponse> listar() {
        return usuarioInternoRepository.findAll().stream()
                .map(UsuarioResponse::desde)
                .toList();
    }

    @Transactional
    public UsuarioResponse cambiarActivo(Long id, boolean activo) {
        UsuarioInterno usuario = usuarioInternoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el usuario con id " + id));

        usuario.setActivo(activo);
        UsuarioResponse actualizado = UsuarioResponse.desde(usuarioInternoRepository.save(usuario));
        registroSistemaService.registrar(TipoRegistroSistema.USUARIO_ACTIVO_CAMBIADO,
                (activo ? "Cuenta activada: " : "Cuenta desactivada: ") + actualizado.correo(), true);
        return actualizado;
    }
}
