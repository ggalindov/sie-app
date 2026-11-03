package sie.siejuridicos.croma.dto;

public record CromaBuzonDespachoDto(
        String email,
        String nombre,
        String tipoCuenta,
        String corporacion,
        String especialidad,
        String departamento,
        String ciudad,
        String seccional,
        String codigoDespacho
) {
}
