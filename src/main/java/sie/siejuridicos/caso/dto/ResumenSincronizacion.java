package sie.siejuridicos.caso.dto;

import java.util.List;

// Resultado de sincronizar automáticamente los casos desde las hojas de la firma (ver
// CasoService.sincronizarDesdeHoja()). Le da al admin visibilidad real de qué pasó, en vez
// de un simple "listo" -- sobre todo útil la primera vez que se corre sobre una hoja con
// muchas filas ya existentes, o si alguna pestaña puntual falló (permisos, red) mientras las
// demás sí se sincronizaron con normalidad.
public record ResumenSincronizacion(
        int filasLeidasEnHoja,
        int casosNuevos,
        int casosActualizados,
        int casosEliminados,
        // El caso SÍ se sincroniza (nunca se pierde, ver CasoService.sincronizarDesdeHoja()):
        // esto cuenta cuántos quedaron sin un correo válido capturado en la hoja, así que
        // todavía no se les puede enviar la notificación del radicado.
        int filasSinCorreo,
        // Filas con un radicado real en la hoja que NO se pudo asignar porque ese mismo
        // texto ya está asignado a OTRO caso (duplicado real en la hoja de la firma, o un
        // caso registrado a mano con ese radicado) -- se detalla cada uno en el log del
        // servidor (ver CasoService.sincronizarDesdeHoja()) para que el admin corrija el
        // duplicado directamente en la hoja.
        int radicadosDuplicados,
        List<String> fuentesConError
) {
}
