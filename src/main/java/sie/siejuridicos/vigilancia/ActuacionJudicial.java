package sie.siejuridicos.vigilancia;

import java.time.LocalDate;
import java.time.LocalDateTime;

// Una actuación real devuelta por el endpoint público de actuaciones de la Rama Judicial
// (ver RamaJudicialCpnuClient). fechaRegistro es la que de verdad importa para buscar la
// providencia en el portal de Publicaciones Procesales (ver PublicacionesProcesalesClient):
// es cuando el despacho la publicó, no la fecha "efectiva" de la actuación (que a veces
// difiere por un día o más).
//
// idActuacionExterna es el idRegActuacion que asigna la propia Rama Judicial -- la llave
// real para guardar el historial sin duplicados (ver ActuacionJudicialRegistrada): fecha +
// nombre NO alcanza, dos actuaciones distintas del mismo día pueden compartir nombre (ej.
// dos "Recepción memorial" el mismo día).
//
// consActuacion, fechaInicialTermino y fechaFinalTermino se verificaron en vivo el
// 2026-10-31 contra el endpoint real del CPNU: existen en la respuesta y este cliente los
// venía descartando. fechaInicialTermino/fechaFinalTermino son el término REAL que el propio
// despacho asocia a esta actuación (cuando aplica, muchas actuaciones no abren ninguno) --
// más autoritativo que cualquier inferencia por palabras clave (ver
// InferenciaTerminoProcesal), que sigue existiendo solo como respaldo cuando estos dos
// vienen null.
//
// Bug real reportado por el usuario (2026-10-31): "necesito... el reporte completo por fecha
// y hora exacta". fechaActuacion/fechaRegistro son LocalDateTime (no LocalDate) a propósito:
// el CPNU sí devuelve la hora exacta ("yyyy-MM-dd'T'HH:mm:ss"), y RamaJudicialCpnuClient la
// venía descartando con un .toLocalDate() que no hacía falta (ver parsearFechaHora allá).
// fechaInicialTermino/fechaFinalTermino SÍ se quedan en LocalDate: el vencimiento de un
// término es un día calendario, la hora no tiene ningún significado de negocio ahí.
public record ActuacionJudicial(
        long idActuacionExterna,
        LocalDateTime fechaActuacion,
        String nombreActuacion,
        String anotacion,
        LocalDateTime fechaRegistro,
        boolean conDocumentos,
        Integer consActuacion,
        LocalDate fechaInicialTermino,
        LocalDate fechaFinalTermino
) {
}
