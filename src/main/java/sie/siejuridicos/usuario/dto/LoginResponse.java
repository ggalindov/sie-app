package sie.siejuridicos.usuario.dto;

import sie.siejuridicos.usuario.RolUsuario;

public record LoginResponse(
        String token,
        String nombre,
        String correo,
        RolUsuario rol,
        long expiraEnMs
) {
}
