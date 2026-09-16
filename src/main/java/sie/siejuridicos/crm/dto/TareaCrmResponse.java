package sie.siejuridicos.crm.dto;

import sie.siejuridicos.crm.PrioridadTareaCrm;
import sie.siejuridicos.crm.TareaCrm;

import java.time.LocalDateTime;

public record TareaCrmResponse(
        Long id,
        Long clienteCrmId,
        String titulo,
        String descripcion,
        LocalDateTime fechaVencimiento,
        boolean completada,
        PrioridadTareaCrm prioridad,
        String usuarioNombre,
        LocalDateTime fechaCreacion
) {
    public static TareaCrmResponse desde(TareaCrm t) {
        return new TareaCrmResponse(
                t.getId(),
                t.getClienteCrm().getId(),
                t.getTitulo(),
                t.getDescripcion(),
                t.getFechaVencimiento(),
                t.isCompletada(),
                t.getPrioridad(),
                t.getUsuarioNombre(),
                t.getFechaCreacion()
        );
    }
}
