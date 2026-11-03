package sie.siejuridicos.vigilancia.dto;

// Lo que ya le mostró DocumentoRecienteResponse al abogado en el panel: él elige UNO de los
// candidatos y lo manda tal cual de vuelta para que el backend lo descargue y lo remita --
// el backend nunca vuelve a adivinar cuál es "el" documento (ver
// EnvioDocumentoJudicialService), solo valida que la URL sea del dominio esperado antes de
// descargarla (ver PublicacionesProcesalesClient.descargarDocumento()).
public record EnviarDocumentoJudicialRequest(
        String urlDocumento,
        String nombreArchivo,
        String nombreActuacion
) {
}
