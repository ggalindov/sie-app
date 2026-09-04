package sie.siejuridicos.cobro.dto;

import sie.siejuridicos.cobro.TipoClienteCobro;

// Una fila leída de alguna de las dos pestañas del Google Sheets de cobros (ver
// HojaCobrosService.listarTodos()). A propósito NUNCA incluye la columna C
// (Dirección/Domicilio) -- ni siquiera se pide a la API, por pedido explícito.
public record FilaCobroHoja(
        TipoClienteCobro tipo,
        String numeroFila,
        String nombre,
        String correo,
        String telefono,
        String cedulaNit,
        String honorarios,
        Boolean pagoEsteMes,
        String respondioMensaje
) {
}
