package sie.siejuridicos.documento.dto;

import sie.siejuridicos.documento.VerificacionVeredicto;

import java.util.List;

// Respuesta de ambos endpoints públicos de verificación. Los campos que no aplican a la
// consulta (por ejemplo 'firmantes' cuando el veredicto es NO_REGISTRADO) viajan como
// null. No incluye PII completa: los nombres de los firmantes van enmascarados.
public record VerificacionPublicaResponse(
        VerificacionVeredicto veredicto,
        String detalleLegal,
        String nombreDocumento,
        String fechaEmision,
        String estadoDocumento,
        String estadoIntegridad,
        Integer version,
        boolean versionVigente,
        String hashSha256,
        List<Firmante> firmantes,
        SelloTiempo selloTiempo,
        CadenaEventos cadenaEventos,
        Boolean firmaAplicacionValida,
        FirmaPades firmaPades,
        List<String> hallazgos
) {

    public record Firmante(String nombreEnmascarado, String rol, String estado, String firmadoEn) {
    }

    public record SelloTiempo(boolean aplicado, String autoridad, String fecha) {
    }

    public record CadenaEventos(boolean integra, Long secuenciaRota, String detalle) {
    }

    // Solo se llena en la verificación de un PDF subido. 'firmasEncontradas' = 0 si el
    // archivo no trae firma digital; 'emitidaPorSie' indica que el certificado coincide
    // con una llave PAdES de SIE; 'cubreTodoElDocumento' = false revela revisiones
    // incrementales posteriores a la firma.
    public record FirmaPades(int firmasEncontradas, boolean firmaValida, boolean emitidaPorSie,
                             boolean cubreTodoElDocumento) {
    }

    public static VerificacionPublicaResponse noRegistrado(String detalle, String hashSha256, FirmaPades pades) {
        return new VerificacionPublicaResponse(VerificacionVeredicto.NO_REGISTRADO, detalle, null, null,
                null, null, null, false, hashSha256, null, null, null, null, pades, List.of());
    }
}
