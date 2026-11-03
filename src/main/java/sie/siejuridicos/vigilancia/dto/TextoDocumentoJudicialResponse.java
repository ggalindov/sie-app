package sie.siejuridicos.vigilancia.dto;

public record TextoDocumentoJudicialResponse(
        boolean disponible,
        String texto,
        String mensaje
) {
    public static TextoDocumentoJudicialResponse disponible(String texto) {
        return new TextoDocumentoJudicialResponse(true, texto, null);
    }

    public static TextoDocumentoJudicialResponse noDisponible(String mensaje) {
        return new TextoDocumentoJudicialResponse(false, null, mensaje);
    }
}
