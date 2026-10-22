package sie.siejuridicos.documento.dto;

import sie.siejuridicos.documento.DocumentoCambioSolicitud;

import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentoCambioSolicitudDto(
        UUID id,
        UUID documentoId,
        String tipoAccion,
        String justificacion,
        String estado,
        String solicitadoPorNombre,
        LocalDateTime solicitadoEn,
        String confirmacionGerenteNombre,
        String confirmacionAbogadoNombre,
        LocalDateTime ejecutadoEn,
        String motivoRechazo
) {
    public static DocumentoCambioSolicitudDto from(DocumentoCambioSolicitud s) {
        return new DocumentoCambioSolicitudDto(
                s.getId(),
                s.getDocumento().getId(),
                s.getTipoAccion(),
                s.getJustificacion(),
                s.getEstado(),
                s.getSolicitadoPor().getNombre(),
                s.getSolicitadoEn(),
                s.getConfirmacionGerente() != null ? s.getConfirmacionGerente().getNombre() : null,
                s.getConfirmacionAbogado() != null ? s.getConfirmacionAbogado().getNombre() : null,
                s.getEjecutadoEn(),
                s.getMotivoRechazo()
        );
    }
}
