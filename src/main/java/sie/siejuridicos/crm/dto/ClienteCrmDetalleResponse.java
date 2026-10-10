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
    // Pedido explícito del usuario: "que dentro del CRM tenga la informacion y ultimo
    // reporte del caso, comentarios y demas, junto al asunto en si del caso". Los últimos
    // cinco campos son el estado real del caso, leído EN VIVO del Google Sheets de la firma
    // (mismo dato que ve un cliente en la consulta pública por radicado, ver
    // CasoConsultaResponse/HojaCalculoService) -- nunca se guardan localmente, así que pueden
    // venir todos en null si el caso todavía no tiene radicado, es de fuente MANUAL, o la hoja
    // no tiene cargada esa fila todavía (no es un error, ver CrmService.obtenerDetalle()).
    public record CasoVinculadoDto(
            Long id,
            String radicadoId,
            String fuente,
            String numeroCaso,
            // nombreCliente da contexto al abrir "Tareas" de este caso (mismo campo que ya usa
            // Casos), notasInternas es la descripción/comentario interno completo del caso.
            String nombreCliente,
            String notasInternas,
            boolean correoEnviado,
            boolean whatsappEnviado,
            String despachoJudicial,
            // El "asunto" del caso: de qué se trata (partes, tipo de proceso).
            String asuntoCaso,
            String tipoCaso,
            // El último reporte/decisión registrado en la hoja de la firma.
            String ultimoReporte,
            String estadoJudicial,
            String fechaActualizacionReporte
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
