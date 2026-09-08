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
        // nombreEnHoja (por caso) antes que cliente.getNombre() (compartido entre todos los
        // casos del mismo cliente, ver Caso.nombreEnHoja): un caso sincronizado desde una
        // hoja siempre trae el nombre real y actual de SU propia fila. Solo los casos MANUAL
        // (nunca vienen de una hoja) caen de vuelta al nombre del Cliente.
        String nombreMostrado = caso.getNombreEnHoja() != null ? caso.getNombreEnHoja() : caso.getCliente().getNombre();
        return new CasoAdminResponse(
                caso.getId(),
                caso.getFuente(),
                caso.getFuente().getNombreVisible(),
                caso.getNumeroCaso(),
                nombreMostrado,
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
