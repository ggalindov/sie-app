package sie.siejuridicos.tarea.dto;

import sie.siejuridicos.tarea.PrioridadTarea;
import sie.siejuridicos.tarea.Tarea;

import java.time.LocalDateTime;

public record TareaResponse(
        Long id,
        Long casoId,
        String casoEtiqueta,
        String clienteNombre,
        String titulo,
        String descripcion,
        LocalDateTime fechaVencimiento,
        PrioridadTarea prioridad,
        boolean completada,
        Long usuarioAsignadoId,
        String usuarioAsignadoNombre,
        Long usuarioCreadorId,
        String usuarioCreadorNombre,
        LocalDateTime fechaCreacion,
        LocalDateTime fechaCompletada
) {
    public static TareaResponse desde(Tarea t) {
        // Mismo criterio que CasoAdminResponse.desde(): el radicado si ya existe, si no el
        // número de caso interno, y si tampoco hay eso, el id -- siempre hay algo legible que
        // mostrar junto al título de la tarea para dar contexto sin otro clic.
        String etiqueta = t.getCaso().getRadicadoId() != null
                ? t.getCaso().getRadicadoId()
                : t.getCaso().getNumeroCaso() != null
                        ? "Caso Nº " + t.getCaso().getNumeroCaso()
                        : "Caso #" + t.getCaso().getId();
        String nombreCliente = t.getCaso().getNombreEnHoja() != null
                ? t.getCaso().getNombreEnHoja()
                : t.getCaso().getCliente().getNombre();

        return new TareaResponse(
                t.getId(),
                t.getCaso().getId(),
                etiqueta,
                nombreCliente,
                t.getTitulo(),
                t.getDescripcion(),
                t.getFechaVencimiento(),
                t.getPrioridad(),
                t.isCompletada(),
                t.getUsuarioAsignado().getId(),
                t.getUsuarioAsignado().getNombre(),
                t.getUsuarioCreador().getId(),
                t.getUsuarioCreador().getNombre(),
                t.getFechaCreacion(),
                t.getFechaCompletada()
        );
    }
}
