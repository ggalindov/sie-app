package sie.siejuridicos.documento;

// Resultado de DocumentoEventoChainVerifier. 'pendiente' distingue "aún no hay ningún
// evento registrado para este documento" de "cadena rota" -- ambos casos devuelven
// integra=true/false según corresponda, pero con un detalle distinto.
public record ResultadoVerificacionCadena(boolean integra, Long secuenciaRota, String detalle) {

    public static ResultadoVerificacionCadena cadenaIntegra() {
        return new ResultadoVerificacionCadena(true, null, null);
    }

    public static ResultadoVerificacionCadena pendiente() {
        return new ResultadoVerificacionCadena(true, null,
                "Aún no se ha registrado ningún evento para este documento.");
    }

    public static ResultadoVerificacionCadena rota(long secuencia, String detalle) {
        return new ResultadoVerificacionCadena(false, secuencia, detalle);
    }
}
