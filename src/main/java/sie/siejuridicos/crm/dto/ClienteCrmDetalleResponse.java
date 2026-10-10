package sie.siejuridicos.crm.dto;

import java.time.LocalDateTime;
import java.util.List;

// El registro de tareas de un cliente ya no viaja aquí -- ver tarea.TareaController
// (GET /api/admin/crm/clientes/{id}/tareas), que junta las tareas ligadas directamente a esta
// persona con las de cualquiera de sus casos en una sola vista.
public record ClienteCrmDetalleResponse(
        ClienteCrmResponse cliente,
        List<CasoVinculadoDto> casos,
        List<CobroVinculadoDto> cobros,
        List<CitaVinculadaDto> citas,
        List<ActividadCrmResponse> actividades
) {
    public record CasoVinculadoDto(
            Long id,
            String radicadoId,
            String fuente,
            String numeroCaso,
            // Pedido explícito del usuario: "pon la descripcion del caso completa dentro de
            // cada perfil del CRM" -- nombreCliente para dar contexto al abrir "Tareas" de este
            // caso (mismo campo que ya usa Casos), notasInternas es la descripción completa.
            String nombreCliente,
            String notasInternas,
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
