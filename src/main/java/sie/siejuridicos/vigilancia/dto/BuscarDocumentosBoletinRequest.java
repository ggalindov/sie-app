package sie.siejuridicos.vigilancia.dto;

import java.time.LocalDate;

// Pedido explícito del usuario: un botón por cada SUCESO de la línea de tiempo (no solo el
// más reciente "de fondo") para buscar el boletín del Estado de esa actuación puntual.
// fechaRegistro y nombreActuacion ya vienen del propio evento mostrado en el panel (ver
// ActuacionRegistradaResponse) -- no hace falta volver a consultar la Rama Judicial (CPNU)
// para esto, solo Publicaciones Procesales con los datos que ya tenemos guardados.
public record BuscarDocumentosBoletinRequest(LocalDate fechaRegistro, String nombreActuacion) {
}
