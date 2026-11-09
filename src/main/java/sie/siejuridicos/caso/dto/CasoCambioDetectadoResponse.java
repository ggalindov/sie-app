package sie.siejuridicos.caso.dto;

import sie.siejuridicos.caso.CasoCambioDetectado;

import java.time.LocalDateTime;

// Para el popup de "casos con cambios detectados" (pedido explícito del usuario, 2026-11-09).
public record CasoCambioDetectadoResponse(
        Long id,
        Long casoId,
        String numeroCaso,
        String nombreCliente,
        String radicadoId,
        String campo,
        String valorAnterior,
        String valorNuevo,
        LocalDateTime fechaDeteccion
) {
    public static CasoCambioDetectadoResponse desde(CasoCambioDetectado cambio) {
        var caso = cambio.getCaso();
        String nombre = caso.getNombreEnHoja() != null && !caso.getNombreEnHoja().isBlank()
                ? caso.getNombreEnHoja()
                : (caso.getCliente() != null ? caso.getCliente().getNombre() : "Cliente");
        return new CasoCambioDetectadoResponse(
                cambio.getId(),
                caso.getId(),
                caso.getNumeroCaso(),
                nombre,
                caso.getRadicadoId(),
                cambio.getCampo(),
                cambio.getValorAnterior(),
                cambio.getValorNuevo(),
                cambio.getFechaDeteccion()
        );
    }
}
