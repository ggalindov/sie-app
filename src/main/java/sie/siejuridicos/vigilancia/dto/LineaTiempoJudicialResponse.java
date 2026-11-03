package sie.siejuridicos.vigilancia.dto;

import sie.siejuridicos.caso.Caso;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

// Línea de tiempo judicial de UN caso puntual (pedido explícito del usuario: "trazabilidad
// independiente por caso... que vaya guardando la historia como una línea de tiempo") --
// envuelve el historial de actuaciones ya guardado (ver ActuacionJudicialRegistrada) junto
// con la trazabilidad propia del caso: si tiene un proceso identificado en la Rama Judicial,
// y cuándo fue la última vez que se revisó (y si esa revisión salió bien), sin tener que
// adivinarlo a partir del resumen agregado de la última corrida completa.
public record LineaTiempoJudicialResponse(
        boolean tieneProcesoCpnu,
        LocalDateTime fechaUltimoSeguimiento,
        Boolean ultimoSeguimientoExitoso,
        // Gap real pedido por el usuario: "lupa nos da un seguimiento mas robusto" -- el
        // propio CPNU ya trae demandante/demandado y el despacho completo (ver
        // RamaJudicialCpnuClient.buscarProceso()), se venía descartando. null hasta que el
        // próximo ciclo de seguimiento lo resuelva (ver SeguimientoJudicialService).
        String sujetosProcesales,
        String despachoJudicial,
        // Pedido explícito del usuario: "tienes que traer absolutamente toda la
        // informacion... entre mas datos mucho mejor" -- ver CasoAdminResponse, mismo origen
        // (RamaJudicialCpnuClient.buscarProceso()/ProcesoResuelto).
        String departamentoJudicial,
        LocalDate fechaRadicacionProceso,
        Boolean procesoPrivado,
        String ponente,
        String tipoProceso,
        String claseProceso,
        String subclaseProceso,
        String recursoProceso,
        String ubicacionProceso,
        // Pedido explícito del usuario: "la vigilancia tiene que mostrar toda, absolutamente
        // toda la informacion... todo lo que se detecte" -- ver CasoAdminResponse, mismo
        // origen (Proceso/Detalle.ultimaActualizacion).
        LocalDateTime ultimaActualizacionRama,
        List<ActuacionRegistradaResponse> eventos
) {
    public static LineaTiempoJudicialResponse desde(Caso caso, List<ActuacionRegistradaResponse> eventos) {
        return new LineaTiempoJudicialResponse(
                caso.getIdProcesoCpnu() != null,
                caso.getFechaUltimoSeguimiento(),
                caso.getUltimoSeguimientoExitoso(),
                caso.getSujetosProcesales(),
                caso.getDespachoJudicial(),
                caso.getDepartamentoJudicial(),
                caso.getFechaRadicacionProceso(),
                caso.getProcesoPrivado(),
                caso.getPonente(),
                caso.getTipoProceso(),
                caso.getClaseProceso(),
                caso.getSubclaseProceso(),
                caso.getRecursoProceso(),
                caso.getUbicacionProceso(),
                caso.getUltimaActualizacionRama(),
                eventos);
    }
}
