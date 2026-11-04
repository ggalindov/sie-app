package sie.siejuridicos.caso.dto;

public record ActualizarCasoRequest(
        String nombreEnHoja,
        String correoCliente,
        String telefonoCliente,
        String radicadoId,
        String notasInternas,
        Long abogadoAsignadoId,
        Boolean esPrivadoCifrado,
        String motivoConfidencialidad
) {
    public ActualizarCasoRequest(String nombreEnHoja, String correoCliente, String telefonoCliente, String radicadoId, String notasInternas, Long abogadoAsignadoId) {
        this(nombreEnHoja, correoCliente, telefonoCliente, radicadoId, notasInternas, abogadoAsignadoId, null, null);
    }
}
