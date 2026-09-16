package sie.siejuridicos.cobro.dto;

public record ActualizarRespuestaCobroRequest(
        String respuesta,
        Boolean pagoEsteMes
) {
}
