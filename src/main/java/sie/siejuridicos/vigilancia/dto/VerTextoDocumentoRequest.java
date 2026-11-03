package sie.siejuridicos.vigilancia.dto;

// La misma URL de un candidato ya mostrado en DocumentoRecienteResponse -- nunca una URL
// arbitraria (PublicacionesProcesalesClient.descargarYExtraerTexto() valida el host de
// todas formas, pero el candidato siempre viene de ahí).
public record VerTextoDocumentoRequest(String urlDocumento) {
}
