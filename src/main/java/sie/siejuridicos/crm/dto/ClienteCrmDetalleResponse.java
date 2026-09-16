package sie.siejuridicos.crm.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ClienteCrmDetalleResponse(
        ClienteCrmResponse cliente,
        List<CasoVinculadoDto> casos,
        List<CobroVinculadoDto> cobros,
        List<CitaVinculadaDto> citas,
        List<ActividadCrmResponse> actividades,
        List<TareaCrmResponse> tareas
) {
    public record CasoVinculadoDto(
            Long id,
            String radicadoId,
            String fuente,
            String numeroCaso,
            boolean correoEnviado,
            boolean whatsappEnviado
    ) {}

    public record CobroVinculadoDto(
            Long id,
            String tipo,
            String numeroFila,
            String honorarios,
            Boolean pagoEsteMes,
            String respondioMensaje,
            LocalDateTime fechaUltimoRecordatorio
    ) {}

    public record CitaVinculadaDto(
            Long id,
            LocalDateTime fechaHora,
            String tipoReunion,
            String linkReunion,
            String lugarReunion
    ) {}
}
