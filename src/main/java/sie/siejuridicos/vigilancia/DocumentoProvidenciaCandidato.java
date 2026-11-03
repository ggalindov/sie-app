package sie.siejuridicos.vigilancia;

// Un documento PDF encontrado en el boletín diario de "Publicaciones Procesales" de la Rama
// Judicial que PODRÍA corresponder a una actuación de nuestro caso. "Podría": el boletín de
// un despacho agrupa los documentos de TODOS los procesos que tuvo ese día, el portal nunca
// nos da el radicado exacto en el nombre del archivo (ver PublicacionesProcesalesClient) --
// solo se emparejan por fecha de publicación + similitud de nombre contra la actuación. Por
// eso esto nunca se usa para seleccionar y enviar un documento solo: es un candidato para
// que un humano confirme.
public record DocumentoProvidenciaCandidato(
        String nombreArchivo,
        String urlDescarga,
        boolean coincideConNombreActuacion
) {
}
