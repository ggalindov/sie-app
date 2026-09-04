package sie.siejuridicos.hojacalculo.dto;

// Una fila encontrada en el Google Sheets de casos de la firma, ya recortada a solo las
// columnas que le interesan a la consulta PÚBLICA por radicado (D, E, F, J, K, M -- ver
// HojaCalculoService.buscarPorRadicado()). A propósito NUNCA incluye el número de caso (B)
// ni el correo/teléfono del cliente (T/U): esos solo los usa la sincronización interna del
// panel (ver FilaSincronizacionHoja), nunca deben poder salir por una ruta pública.
//
// Los valores se dejan tal cual los devuelve Google (FORMATTED_VALUE): ninguno se parsea
// (ni la fecha), para no depender de asumir un formato/zona horaria que puede cambiar si
// alguien en la firma edita el formato de esa columna en la hoja.
public record FilaCasoHoja(
        String despachoJudicial,
        String informacionCaso,
        String tipoCaso,
        String ultimaDecision,
        String estado,
        String fechaActualizacion
) {
}
