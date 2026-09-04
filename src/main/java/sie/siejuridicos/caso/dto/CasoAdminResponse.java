package sie.siejuridicos.caso.dto;

import sie.siejuridicos.caso.Caso;
import sie.siejuridicos.caso.FuenteCaso;

import java.time.LocalDateTime;

public record CasoAdminResponse(
        Long id,
        FuenteCaso fuente,
        String fuenteVisible,
        String numeroCaso,
        String nombreCliente,
        String correoCliente,
        String telefonoCliente,
        String radicadoId,
        boolean correoEnviado,
        boolean whatsappEnviado,
        String notasInternas,
        LocalDateTime fechaCreacion
) {
    public static CasoAdminResponse desde(Caso caso) {
        return new CasoAdminResponse(
                caso.getId(),
                caso.getFuente(),
                caso.getFuente().getNombreVisible(),
                caso.getNumeroCaso(),
                caso.getCliente().getNombre(),
                caso.getCliente().getCorreo(),
                caso.getCliente().getTelefono(),
                caso.getRadicadoId(),
                caso.isCorreoEnviado(),
                caso.isWhatsappEnviado(),
                caso.getNotasInternas(),
                caso.getFechaCreacion()
        );
    }
}
