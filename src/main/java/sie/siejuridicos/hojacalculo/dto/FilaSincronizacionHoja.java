package sie.siejuridicos.hojacalculo.dto;

import sie.siejuridicos.caso.FuenteCaso;

// Una fila de alguna de las hojas de la firma lista para sincronizar como Caso+Cliente
// local (ver HojaCalculoService.listarParaSincronizar() y
// CasoService.sincronizarDesdeHoja()). A diferencia de FilaCasoHoja (la que consume la
// consulta pública), este tipo SÍ trae datos de identificación del cliente -- por diseño
// solo lo usa el flujo interno del panel administrativo, nunca una ruta pública.
//
// radicadoId, nombreCliente, correoCliente y telefonoCliente pueden ser null (columna
// vacía en la hoja, o la hoja de origen no tiene esa columna en absoluto -- ver
// HojaCalculoService): un caso puede quedar registrado sin radicado todavía, y una fila sin
// correo del cliente simplemente se omite en la sincronización (no hay forma de
// crear/identificar un Cliente sin su correo).
public record FilaSincronizacionHoja(
        FuenteCaso fuente,
        String numeroCaso,
        String radicadoId,
        String nombreCliente,
        String correoCliente,
        String telefonoCliente
) {
}
