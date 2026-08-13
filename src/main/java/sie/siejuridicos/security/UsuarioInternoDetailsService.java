package sie.siejuridicos.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import sie.siejuridicos.usuario.UsuarioInternoRepository;

@Service
public class UsuarioInternoDetailsService implements UserDetailsService {

    private final UsuarioInternoRepository usuarioInternoRepository;

    public UsuarioInternoDetailsService(UsuarioInternoRepository usuarioInternoRepository) {
        this.usuarioInternoRepository = usuarioInternoRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String correo) throws UsernameNotFoundException {
        return usuarioInternoRepository.findByCorreo(correo)
                .map(UsuarioInternoPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("No existe un usuario con correo " + correo));
    }
}
