package sie.siejuridicos.hojacalculo.dto;

import sie.siejuridicos.caso.FuenteCaso;

import java.util.List;

// Resultado de leer las tres hojas para sincronizar (ver
// HojaCalculoService.listarParaSincronizar()). fuentesConError nunca detiene la
// sincronización de las demás: son tres llamadas HTTP independientes a Google, y un fallo
// puntual en una (red, permisos de esa pestaña en particular) no debería impedir que las
// otras dos sí se sincronicen.
public record ResultadoSincronizacionHoja(
        List<FilaSincronizacionHoja> filas,
        List<FuenteCaso> fuentesConError
) {
}
