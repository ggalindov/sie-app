package sie.siejuridicos.solicitud;

// Ver Solicitud.tipoReunion / SolicitudService.agendarCita: decide si la reunion agendada
// se valida y se comunica con un link (VIRTUAL, Meet o Zoom) o con una direccion fisica
// (PRESENCIAL) -- nunca ambos a la vez.
public enum TipoReunion {
    VIRTUAL,
    PRESENCIAL
}
