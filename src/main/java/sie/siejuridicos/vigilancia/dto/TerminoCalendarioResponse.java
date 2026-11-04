package sie.siejuridicos.vigilancia.dto;

import sie.siejuridicos.caso.Caso;
import sie.siejuridicos.caso.RadicadoUtil;
import sie.siejuridicos.croma.dto.CromaCasoDto;
import sie.siejuridicos.vigilancia.ActuacionJudicialRegistrada;
import sie.siejuridicos.vigilancia.BoletinJudicialCache;
import sie.siejuridicos.vigilancia.InferenciaTerminoProcesal;
import sie.siejuridicos.vigilancia.TerminoProcesal;

import java.time.LocalDate;

// Vista de un TerminoProcesal para /admin/calendario.
// Incluye enlace directo a la providencia / boletín oficial y a la notificación oficial en la Rama Judicial (CPNU/SAMAI).
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
        String abogadoAsignadoNombre,
        Long actuacionId,
        String nombreActuacion,
        String anotacionActuacion,
        LocalDate fechaActuacion,
        String enlaceRamaJudicial,
        Long boletinId,
        String nombreBoletin,
        String urlBoletinPdf,
        String rama
) {
    public static TerminoCalendarioResponse desde(TerminoProcesal termino) {
        return desde(termino, null);
    }

    public static TerminoCalendarioResponse desde(TerminoProcesal termino, BoletinJudicialCache boletin) {
        Caso caso = termino.getCaso();
        String nombreMostrado = caso.getNombreEnHoja() != null ? caso.getNombreEnHoja() : caso.getCliente().getNombre();
        ActuacionJudicialRegistrada act = termino.getActuacion();

        String enlaceRama = construirEnlaceRama(caso.getRadicadoId());
        String rama = CromaCasoDto.inferirRama(caso.getDespachoJudicial(), caso.getClaseProceso());

        Long actuacionId = act != null ? act.getId() : null;
        String nombreActuacion = act != null ? act.getNombreActuacion() : null;
        String anotacion = act != null ? act.getAnotacion() : null;
        LocalDate fechaAct = act != null && act.getFechaActuacion() != null ? act.getFechaActuacion().toLocalDate() : null;

        Long boletinId = boletin != null ? boletin.getId() : null;
        String nombreBoletin = boletin != null ? boletin.getNombreArchivo() : null;
        String urlBoletinPdf = boletin != null ? "/api/admin/vigilancia/boletines/" + boletin.getId() + "/pdf" : null;

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
                caso.getAbogadoAsignado() != null ? caso.getAbogadoAsignado().getNombre() : null,
                actuacionId,
                nombreActuacion,
                anotacion,
                fechaAct,
                enlaceRama,
                boletinId,
                nombreBoletin,
                urlBoletinPdf,
                rama
        );
    }

    public static String construirEnlaceRama(String radicado) {
        if (radicado == null || radicado.isBlank()) return null;
        String limpio = RadicadoUtil.limpiar(radicado);
        if (limpio.startsWith("1100103") || limpio.startsWith("2500023") || limpio.startsWith("1100133") || limpio.length() < 23) {
            return "https://samai.consejodeestado.gov.co/Vistas/Casos/procesos.aspx";
        }
        return "https://consultaprocesos.ramajudicial.gov.co/Procesos/NumeroRadicacion?numero=" + limpio;
    }
}
