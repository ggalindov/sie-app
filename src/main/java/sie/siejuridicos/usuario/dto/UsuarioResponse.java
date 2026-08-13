package sie.siejuridicos.usuario.dto;

import sie.siejuridicos.usuario.RolUsuario;
import sie.siejuridicos.usuario.UsuarioInterno;

import java.time.LocalDateTime;

public record UsuarioResponse(
        Long id,
        String nombre,
        String correo,
        RolUsuario rol,
        boolean activo,
        LocalDateTime fechaCreacion
) {
    public static UsuarioResponse desde(UsuarioInterno usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getCorreo(),
                usuario.getRol(),
                usuario.isActivo(),
                usuario.getFechaCreacion()
        );
    }
}
