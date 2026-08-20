package sie.siejuridicos.caso.dto;

import sie.siejuridicos.caso.Caso;
import sie.siejuridicos.caso.EtapaCaso;

import java.time.LocalDateTime;

public record CasoAdminResponse(
        Long id,
        String nombreCliente,
        String correoCliente,
        String telefonoCliente,
        String categoriaNombre,
        String codigoUnico,
        EtapaCaso etapa,
        String notasInternas,
        LocalDateTime fechaCreacion,
        LocalDateTime fechaActualizacion
) {
    public static CasoAdminResponse desde(Caso caso) {
        return new CasoAdminResponse(
                caso.getId(),
                caso.getCliente().getNombre(),
                caso.getCliente().getCorreo(),
                caso.getCliente().getTelefono(),
                caso.getCategoria().getNombre(),
                caso.getCodigoUnico(),
                caso.getEtapa(),
                caso.getNotasInternas(),
                caso.getFechaCreacion(),
                caso.getFechaActualizacion()
        );
    }
}
