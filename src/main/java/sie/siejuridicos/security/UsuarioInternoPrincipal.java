package sie.siejuridicos.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import sie.siejuridicos.usuario.UsuarioInterno;

import java.util.Collection;
import java.util.List;

public class UsuarioInternoPrincipal implements UserDetails {

    private final UsuarioInterno usuario;

    public UsuarioInternoPrincipal(UsuarioInterno usuario) {
        this.usuario = usuario;
    }

    public Long getId() {
        return usuario.getId();
    }

    public UsuarioInterno getUsuario() {
        return usuario;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getRol().name()));
    }

    @Override
    public String getPassword() {
        return usuario.getContrasena();
    }

    @Override
    public String getUsername() {
        return usuario.getCorreo();
    }

    @Override
    public boolean isEnabled() {
        return usuario.isActivo();
    }
}
