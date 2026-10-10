package sie.siejuridicos.tarea.dto;

import sie.siejuridicos.tarea.PrioridadTarea;
import sie.siejuridicos.tarea.Tarea;

import java.time.LocalDateTime;

public record TareaResponse(
        Long id,
        Long casoId,
        Long clienteCrmId,
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
    // Una tarea ahora puede estar ligada a un Caso, a una persona del CRM directamente, o a
    // ambos (ver migración V46) -- nunca a ninguno de los dos a la vez (lo garantiza el CHECK
    // de la base de datos). casoEtiqueta/clienteNombre siempre dan contexto legible sin importar
    // cuál de los dos esté presente.
    public static TareaResponse desde(Tarea t) {
        Long casoId = t.getCaso() != null ? t.getCaso().getId() : null;
        Long clienteCrmId = t.getClienteCrm() != null ? t.getClienteCrm().getId() : null;

        String etiqueta;
        String nombreCliente;
        if (t.getCaso() != null) {
            // Mismo criterio que CasoAdminResponse.desde(): el radicado si ya existe, si no el
            // número de caso interno, y si tampoco hay eso, el id.
            etiqueta = t.getCaso().getRadicadoId() != null
                    ? t.getCaso().getRadicadoId()
                    : t.getCaso().getNumeroCaso() != null
                            ? "Caso Nº " + t.getCaso().getNumeroCaso()
                            : "Caso #" + t.getCaso().getId();
            nombreCliente = t.getCaso().getNombreEnHoja() != null
                    ? t.getCaso().getNombreEnHoja()
                    : t.getCaso().getCliente().getNombre();
        } else {
            etiqueta = "Tarea general";
            nombreCliente = t.getClienteCrm() != null ? t.getClienteCrm().getNombre() : "Sin asignar";
        }

        return new TareaResponse(
                t.getId(),
                casoId,
                clienteCrmId,
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
