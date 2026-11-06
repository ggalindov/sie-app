package sie.siejuridicos.vigilancia.dto;

import java.time.LocalDateTime;

// Pedido explícito del usuario (2026-11-05): "en notificaciones muestre todos los casos que
// el ultimo dia tuvieron alguna actualizacion, gerencia le salen todos, abogado todos los
// asignados a esa persona" -- una fila por caso (nunca una por actuación: un caso con varios
// movimientos en el día aparece una sola vez, con el movimiento más reciente), para el panel
// de Notificaciones (ver VigilanciaAdminController.casosActualizadosRecientes()).
public record CasoActualizadoResponse(
        Long casoId,
        String nombreCliente,
        String radicadoId,
        String nombreActuacion,
        LocalDateTime fechaMovimiento
) {
}
