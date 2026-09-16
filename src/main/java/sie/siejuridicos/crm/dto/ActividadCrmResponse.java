package sie.siejuridicos.crm.dto;

import sie.siejuridicos.crm.ActividadCrm;
import sie.siejuridicos.crm.TipoActividadCrm;

import java.time.LocalDateTime;

public record ActividadCrmResponse(
        Long id,
        Long clienteCrmId,
        Long solicitudId,
        Long casoId,
        TipoActividadCrm tipo,
        String titulo,
        String descripcion,
        String usuarioNombre,
        LocalDateTime fechaActividad,
        LocalDateTime fechaCreacion
) {
    public static ActividadCrmResponse desde(ActividadCrm a) {
        return new ActividadCrmResponse(
                a.getId(),
                a.getClienteCrm().getId(),
                a.getSolicitudId(),
                a.getCasoId(),
                a.getTipo(),
                a.getTitulo(),
                a.getDescripcion(),
                a.getUsuarioNombre(),
                a.getFechaActividad(),
                a.getFechaCreacion()
        );
    }
}
