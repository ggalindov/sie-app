package sie.siejuridicos.documento.dto;

public record DocumentDownload(byte[] contenido, String nombreArchivo, String contentType) {
}
