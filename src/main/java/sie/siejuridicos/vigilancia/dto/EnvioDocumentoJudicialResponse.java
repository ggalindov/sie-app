package sie.siejuridicos.vigilancia.dto;

public record EnvioDocumentoJudicialResponse(
        EstadoEnvioDocumentoJudicial estado,
        String mensaje
) {
    public static EnvioDocumentoJudicialResponse enviado() {
        return new EnvioDocumentoJudicialResponse(EstadoEnvioDocumentoJudicial.ENVIADO,
                "El documento se remitió al correo del cliente.");
    }

    public static EnvioDocumentoJudicialResponse bloqueadoSeguridad() {
        return new EnvioDocumentoJudicialResponse(EstadoEnvioDocumentoJudicial.BLOQUEADO_SEGURIDAD,
                "No se envió nada de verdad: app.bloqueo-total-clientes está activo en este entorno "
                        + "(modo de seguridad de desarrollo). Esto es exactamente lo esperado fuera de producción.");
    }

    public static EnvioDocumentoJudicialResponse fallido(String motivo) {
        return new EnvioDocumentoJudicialResponse(EstadoEnvioDocumentoJudicial.FALLIDO, motivo);
    }
}
