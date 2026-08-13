package sie.siejuridicos.usuario;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sie.siejuridicos.common.exception.ConflictoNegocioException;
import sie.siejuridicos.common.exception.RecursoNoEncontradoException;
import sie.siejuridicos.usuario.dto.CrearUsuarioRequest;
import sie.siejuridicos.usuario.dto.UsuarioResponse;

import java.util.List;

@Service
public class UsuarioInternoService {

    private final UsuarioInternoRepository usuarioInternoRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioInternoService(UsuarioInternoRepository usuarioInternoRepository, PasswordEncoder passwordEncoder) {
        this.usuarioInternoRepository = usuarioInternoRepository;
        this.passwordEncoder = passwordEncoder;
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

        return UsuarioResponse.desde(usuarioInternoRepository.save(usuario));
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
        return UsuarioResponse.desde(usuarioInternoRepository.save(usuario));
    }
}
