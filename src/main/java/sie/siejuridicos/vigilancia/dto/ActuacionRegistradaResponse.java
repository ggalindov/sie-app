package sie.siejuridicos.vigilancia.dto;

import sie.siejuridicos.vigilancia.ActuacionJudicialRegistrada;
import sie.siejuridicos.vigilancia.InferenciaTerminoProcesal;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

// Vista de una ActuacionJudicialRegistrada para el panel admin -- nunca se expone la
// entidad JPA directamente por REST (mismo criterio que el resto del sistema).
//
// Los campos de término (descripcionTermino...estadoTermino) son SIEMPRE calculados al vuelo
// aquí, nunca persistidos tal cual (ver InferenciaTerminoProcesal) -- null cuando la
// actuación no abre ningún término reconocido. estadoTermino ya viene traducido a texto
// porque es un enum interno del paquete vigilancia, no expuesto como tipo propio por REST.
//
// terminoEsOficial distingue un HECHO informado directamente por la Rama Judicial
// (fechaInicialTermino/fechaFinalTermino reales, ver ActuacionJudicialRegistrada) de una
// simple inferencia por palabras clave -- pedido explícito del usuario: "no podemos reducir
// nada... tiene que estar todo de manera ultra rigurosa". consecutivo es el número real que
// el despacho le asignó a esta actuación dentro de su propio proceso.
public record ActuacionRegistradaResponse(
        // Pedido explícito del usuario (2026-10-31, "implementa algo mas rapido... que el
        // sistema lo guarde en el servidor"): antes este DTO no exponía el id real de la
        // actuación ("no hay un id propio... expuesto por REST todavía") -- hacía falta para
        // poder cruzar cada evento de la línea de tiempo con lo que YA está en
        // BoletinJudicialCache (ver VigilanciaAdminController.boletinesCache()) sin depender
        // de volver a consultar Publicaciones Procesales en vivo por cada actuación cada vez
        // que se abre el modal de Seguimiento.
        Long id,
        // LocalDateTime desde la migración V65 -- pedido explícito del usuario: "fecha y hora
        // exacta". El CPNU sí la trae, ver ActuacionJudicial/RamaJudicialCpnuClient.
        LocalDateTime fechaActuacion,
        String nombreActuacion,
        String anotacion,
        LocalDateTime fechaRegistro,
        boolean conDocumentos,
        boolean esFundamental,
        LocalDateTime fechaDetectada,
        Integer consecutivo,
        String descripcionTermino,
        Integer diasHabilesTermino,
        LocalDate fechaVencimientoTermino,
        String estadoTermino,
        Boolean terminoEsOficial,
        // Pedido explícito del usuario: "implementa todas las mejoras identificadas"
        // (comparando contra Lupa Jurídica, ver memoria del proyecto) -- Lupa destaca con un
        // flag aparte cuando una actuación trae liquidación de costas o menciona un acreedor
        // subrogatorio (eventos con impacto económico directo para el cliente). Mismo
        // criterio que InferenciaTerminoProcesal: una búsqueda de palabras clave sobre el
        // nombre/anotación de la actuación, NUNCA un hecho verificado -- un abogado debe
        // confirmar leyendo el auto real antes de actuar sobre esto.
        boolean posibleLiquidacionCostas,
        boolean posibleAcreedorSubrogatorio,
        // Gap real detectado revisando los correos reales de Lupa Jurídica de la propia
        // firma (pedido explícito del usuario: "mejores la vigilancia... saca informaciones
        // de portales abiertos") -- su columna "INFORMACIÓN ADICIONAL" también marca en rojo
        // "MEDIDAS CAUTELARES" además de "ACREEDOR SUBROGATORIO". Mismo criterio que los dos
        // de arriba: palabras clave, nunca un hecho verificado.
        boolean posibleMedidaCautelar
) {
    public static ActuacionRegistradaResponse desde(ActuacionJudicialRegistrada entidad) {
        boolean oficial = entidad.getFechaInicialTermino() != null && entidad.getFechaFinalTermino() != null;
        LocalDate fechaRegistroSolo = entidad.getFechaRegistro() != null ? entidad.getFechaRegistro().toLocalDate() : null;
        var termino = oficial
                ? Optional.of(InferenciaTerminoProcesal.desdeFechaOficial(
                        entidad.getFechaInicialTermino(), entidad.getFechaFinalTermino()))
                : InferenciaTerminoProcesal.inferir(entidad.getNombreActuacion(), fechaRegistroSolo);
        String textoCompleto = (safe(entidad.getNombreActuacion()) + " " + safe(entidad.getAnotacion()))
                .toLowerCase(Locale.ROOT);
        return new ActuacionRegistradaResponse(
                entidad.getId(),
                entidad.getFechaActuacion(),
                entidad.getNombreActuacion(),
                entidad.getAnotacion(),
                entidad.getFechaRegistro(),
                entidad.isConDocumentos(),
                entidad.isEsFundamental(),
                entidad.getFechaDetectada(),
                entidad.getConsecutivo(),
                termino.map(InferenciaTerminoProcesal.TerminoInferido::descripcion).orElse(null),
                termino.map(InferenciaTerminoProcesal.TerminoInferido::diasHabiles).orElse(null),
                termino.map(InferenciaTerminoProcesal.TerminoInferido::fechaVencimiento).orElse(null),
                termino.map(t -> t.estado().name()).orElse(null),
                termino.isPresent() ? oficial : null,
                contieneAlguna(textoCompleto, "liquidacion de costas", "liquidación de costas", "liquida costas"),
                contieneAlguna(textoCompleto, "subrogatorio", "subrogatoria"),
                contieneAlguna(textoCompleto, "medida cautelar", "medidas cautelares", "cautelar"));
    }

    private static String safe(String texto) {
        return texto == null ? "" : texto;
    }

    private static boolean contieneAlguna(String textoNormalizado, String... claves) {
        for (String clave : claves) {
            if (textoNormalizado.contains(clave)) {
                return true;
            }
        }
        return false;
    }
}
