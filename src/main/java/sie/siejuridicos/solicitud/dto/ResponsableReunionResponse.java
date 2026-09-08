package sie.siejuridicos.solicitud.dto;

import sie.siejuridicos.usuario.RolUsuario;
import sie.siejuridicos.usuario.UsuarioInterno;

// Usuario interno activo que puede quedar como responsable de una reunión (ver
// SolicitudService.listarResponsables) -- alimenta el selector "Responsable" que ve
// ADMIN_GENERAL al agendar (un ABOGADO nunca lo ve: siempre queda asignado a sí mismo).
public record ResponsableReunionResponse(Long id, String nombre, RolUsuario rol) {
    public static ResponsableReunionResponse desde(UsuarioInterno usuario) {
        return new ResponsableReunionResponse(usuario.getId(), usuario.getNombre(), usuario.getRol());
    }
}
