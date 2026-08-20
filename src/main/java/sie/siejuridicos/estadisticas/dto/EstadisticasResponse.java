package sie.siejuridicos.estadisticas.dto;

import java.util.Map;

// Resumen agregado para el dashboard del panel administrativo. Los mapas por estado
// siempre incluyen todas las claves del enum correspondiente (con 0 si no hay datos aún),
// para que el frontend no tenga que manejar claves ausentes.
//
// A propósito, este DTO NO incluye el gasto en dólares del chatbot: esa cifra la controla
// el gobernador de presupuesto en ChatbotService (que sí sigue midiéndola en la base de
// datos para su propia lógica), pero no se expone en el panel administrativo — el dueño de
// la firma la revisa directamente en el balance de Anthropic, no aquí.
public record EstadisticasResponse(
        Map<String, Long> solicitudesPorEstado,
        Map<String, Long> solicitudesPorOrigen,
        long citasAgendadas,
        long citasProximas,
        long solicitudesUltimos7Dias,
        Map<String, Long> testimoniosPorEstado,
        int conversacionesChatbotMesActual,
        int limiteMensualChatbot,
        long articulosPublicados,
        long articulosBorrador,
        long suscriptoresMarketingActivos,
        long usuariosInternosActivos,
        Map<String, Long> usuariosPorRol,
        long visitantesMesActual
) {
}
