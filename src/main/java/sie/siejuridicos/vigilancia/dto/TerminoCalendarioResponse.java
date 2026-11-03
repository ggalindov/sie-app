package sie.siejuridicos.vigilancia.dto;

import sie.siejuridicos.caso.Caso;
import sie.siejuridicos.vigilancia.InferenciaTerminoProcesal;
import sie.siejuridicos.vigilancia.TerminoProcesal;

import java.time.LocalDate;

// Vista de un TerminoProcesal para /admin/calendario (gap real detectado en la auditoria del
// plan de desarrollo: "el termino solo se ve como una insignia dentro del modal de
// Seguimiento... no hay ninguna alerta activa en otro lado del panel"). "estado" se
// recalcula al vuelo aqui (ver InferenciaTerminoProcesal.calcularEstado), nunca se persiste
// -- depende de la fecha de HOY.
public record TerminoCalendarioResponse(
        Long id,
        Long casoId,
        String nombreCliente,
        String radicadoId,
        String descripcion,
        LocalDate fechaVencimiento,
        int diasHabiles,
        String estado,
        boolean esOficial,
        Long abogadoAsignadoId,
        String abogadoAsignadoNombre
) {
    public static TerminoCalendarioResponse desde(TerminoProcesal termino) {
        Caso caso = termino.getCaso();
        String nombreMostrado = caso.getNombreEnHoja() != null ? caso.getNombreEnHoja() : caso.getCliente().getNombre();
        return new TerminoCalendarioResponse(
                termino.getId(),
                caso.getId(),
                nombreMostrado,
                caso.getRadicadoId(),
                termino.getDescripcion(),
                termino.getFechaVencimiento(),
                termino.getDiasHabiles(),
                InferenciaTerminoProcesal.calcularEstado(termino.getFechaVencimiento()).name(),
                termino.isEsOficial(),
                caso.getAbogadoAsignado() != null ? caso.getAbogadoAsignado().getId() : null,
                caso.getAbogadoAsignado() != null ? caso.getAbogadoAsignado().getNombre() : null
        );
    }
}
