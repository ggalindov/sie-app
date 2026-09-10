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
        LocalDateTime fechaCreacion,
        LocalDateTime fechaUltimoReporteSemanal,
        LocalDateTime fechaUltimoReporteWhatsapp
) {
    public static CasoAdminResponse desde(Caso caso) {
        String nombreMostrado = caso.getNombreEnHoja() != null ? caso.getNombreEnHoja() : caso.getCliente().getNombre();
        // Si el caso ya tiene confirmada la notificación de radicado o el reporte periódico por ese canal,
        // se refleja fielmente como enviado en el panel administrativo.
        boolean correoFueEnviado = caso.isCorreoEnviado() || caso.getFechaUltimoReporteSemanal() != null;
        boolean whatsappFueEnviado = caso.isWhatsappEnviado() || caso.getFechaUltimoReporteWhatsapp() != null;

        return new CasoAdminResponse(
                caso.getId(),
                caso.getFuente(),
                caso.getFuente().getNombreVisible(),
                caso.getNumeroCaso(),
                nombreMostrado,
                caso.getCliente().getCorreo(),
                caso.getCliente().getTelefono(),
                caso.getRadicadoId(),
                correoFueEnviado,
                whatsappFueEnviado,
                caso.getNotasInternas(),
                caso.getFechaCreacion(),
                caso.getFechaUltimoReporteSemanal(),
                caso.getFechaUltimoReporteWhatsapp()
        );
    }
}
