package sie.siejuridicos.vigilancia.dto;

import java.time.LocalDateTime;

// Gap real detectado comparando en vivo contra LUPA Jurídica (su "AGENDA DE DILIGENCIAS"):
// una audiencia que la Rama Judicial fija dentro de una actuación, con fecha extraída del
// texto (ver ExtractorFechaAudiencia) -- nunca un hecho verificado por un humano, siempre
// hay que leer el auto real antes de confiar en la fecha/hora exacta (ver
// VigilanciaAdminController.audienciasCalendario).
public record AudienciaCalendarioResponse(
        Long casoId,
        String nombreCliente,
        String radicadoId,
        Long actuacionId,
        String nombreActuacion,
        String anotacion,
        LocalDateTime fechaHora,
        boolean horaEstimada,
        // Enlace de videoconferencia detectado en el mismo texto (pedido explícito del
        // usuario, 2026-11-06: "si se puede link de una audiencia"), null si no se encontró
        // ninguno -- nunca verificado, el abogado debe confirmarlo leyendo el auto real.
        String enlace,
        Long abogadoAsignadoId,
        String abogadoAsignadoNombre
) {
}
