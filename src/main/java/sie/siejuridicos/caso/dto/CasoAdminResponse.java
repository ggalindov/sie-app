package sie.siejuridicos.caso.dto;

import sie.siejuridicos.caso.Caso;
import sie.siejuridicos.caso.FuenteCaso;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record CasoAdminResponse(
        Long id,
        FuenteCaso fuente,
        String fuenteVisible,
        String numeroCaso,
        String nombreCliente,
        String correoCliente,
        String telefonoCliente,
        String radicadoId,
        boolean correoEnviado,
        boolean whatsappEnviado,
        String notasInternas,
        LocalDateTime fechaCreacion,
        LocalDateTime fechaUltimoReporteSemanal,
        LocalDateTime fechaUltimoReporteWhatsapp,
        // Trazabilidad del seguimiento judicial 24/7 (ver
        // sie.siejuridicos.vigilancia.SeguimientoJudicialService) -- visible desde el listado
        // general de casos sin tener que entrar a la línea de tiempo de cada uno.
        boolean tieneProcesoCpnu,
        LocalDateTime fechaUltimoSeguimiento,
        Boolean ultimoSeguimientoExitoso,
        // Pedido explícito del usuario: "si es nuevo para cada persona, si no la ha abierto
        // le sale un botoncito rojo" -- true cuando este caso tiene al menos una actuación
        // detectada DESPUÉS de la última vez que el usuario ACTUAL (no global) abrió su línea
        // de tiempo. Se calcula en CasoService.listarTodos(), nunca aquí (esta clase no tiene
        // acceso a quién está pidiendo el listado).
        boolean tieneActualizacionSinRevisar,
        // Pedido explícito del usuario: el ordenador de "actualización" NO debe usar
        // fechaUltimoSeguimiento (eso es solo "cuándo CORRIÓ el chequeo", que pasa para
        // todos los casos cada ciclo aunque no haya pasado nada) sino "la fecha en la que se
        // encontró el último reporte o notificación... para ver arriba los que han tenido
        // movimiento" -- es decir, cuándo se DETECTÓ realmente una actuación nueva
        // (MAX(fecha_detectada) de ActuacionJudicialRegistrada). Null si el caso nunca ha
        // tenido ninguna actuación detectada.
        LocalDateTime fechaUltimaActuacionDetectada,
        // Corrección real pedida por el usuario: "un caso que tuvo movimiento hoy tiene que
        // salir arriba" -- a diferencia del campo de arriba (cuándo NUESTRO sistema vio la
        // actuación por primera vez), esta es la fecha real en que el despacho registró el
        // movimiento (ver ActuacionJudicialRegistradaRepository.ultimaFechaMovimientoPorCaso()).
        // Es la que debe usarse para ordenar "por última actualización" en el listado.
        // LocalDateTime desde la migración V65: ahora también trae la hora exacta real del
        // movimiento (pedido explícito del usuario), no solo el día.
        LocalDateTime fechaUltimoMovimientoJudicial,
        // Pedido explicito del usuario: "asignacion de caso por abogado" (ver migracion
        // V61) -- null cuando el caso todavia no tiene abogado asignado. Visible para
        // cualquier rol (no solo quien puede editarlo).
        Long abogadoAsignadoId,
        String abogadoAsignadoNombre,
        // Pedido explícito del usuario: punto azul junto al de "Seguimiento" en el listado,
        // más "un contador en cada caso con boletin... que muestre dentro de cuantos dias
        // seran borrados" -- null cuando el caso no tiene ningún boletín cacheado (el punto
        // azul no se muestra); si tiene varios con distinta fecha de expiración, este es el
        // de la expiración MÁS PRÓXIMA (el que de verdad apura, ver
        // BoletinJudicialCacheRepository.proximaExpiracionPorCaso()). 0 significa "se borra
        // hoy", nunca negativo (el propio purgarExpirados() ya lo habrá borrado antes de que
        // este número pueda llegar a serlo).
        Integer diasParaBorrarBoletin,
        // Gap real detectado comparando contra el correo diario de Lupa Jurídica (pedido
        // explícito del usuario: "lupa nos da un seguimiento mas robusto... necesito mejorar
        // esto") -- el propio CPNU ya da demandante/demandado y el despacho completo al
        // resolver idProcesoCpnu (ver RamaJudicialCpnuClient.buscarProceso()), se venía
        // descartando. null hasta que el próximo ciclo de seguimiento lo resuelva para este
        // caso (ver SeguimientoJudicialService.sincronizarCaso()).
        String sujetosProcesales,
        String despachoJudicial,
        // Pedido explícito del usuario: "tienes que traer absolutamente toda la
        // informacion... entre mas datos mucho mejor" -- el resto de lo que da el CPNU al
        // resolver el proceso (ver RamaJudicialCpnuClient.buscarProceso()/ProcesoResuelto),
        // incluido un endpoint APARTE (Proceso/Detalle) que NumeroRadicacion no trae.
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
        // toda la informacion... todo lo que se detecte" -- cuándo la Rama Judicial misma
        // actualizó este proceso en SU sistema (Proceso/Detalle.ultimaActualizacion), nunca
        // confundir con fechaUltimoSeguimiento (cuándo NOSOTROS revisamos el caso).
        LocalDateTime ultimaActualizacionRama,
        // Pedido explícito del usuario (2026-11-01): "el sistema debe reflejar que fallos
        // hay" -- true cuando radicadoId, aunque tenga un valor, no es un radicado CPNU real
        // de 23 dígitos (ver Caso.setRadicadoId()/RadicadoUtil). NUNCA significa que el caso
        // esté mal -- hay jurisdicciones reales sin radicado CPNU (comisaría de familia,
        // inspección de policía) -- solo que este caso puntual no tendrá seguimiento
        // automático 24/7 hasta que se corrija o se confirme que es de otro tipo.
        boolean radicadoFormatoInvalido,
        boolean esPrivadoCifrado,
        String motivoConfidencialidad,
        // Pedido explícito del usuario (2026-11-05): "un apartado de casos finalizados...
        // guarde todo el ultimo seguimiento" -- el panel de Casos Finalizados muestra estos 3
        // campos junto con el resto del estado ya resuelto arriba (que nunca se borra ni se
        // vuelve a tocar una vez finalizado, ver Caso.finalizado).
        boolean finalizado,
        LocalDateTime fechaFinalizacion,
        String finalizadoPorNombre
) {
    public static CasoAdminResponse desde(
            Caso caso, boolean tieneActualizacionSinRevisar, LocalDateTime fechaUltimaActuacionDetectada,
            LocalDateTime fechaUltimoMovimientoJudicial, Integer diasParaBorrarBoletin) {
        String nombreMostrado = caso.getNombreEnHoja() != null ? caso.getNombreEnHoja() : caso.getCliente().getNombre();
        // Si el caso ya tiene confirmada la notificación de radicado o el reporte periódico por ese canal,
        // se refleja fielmente como enviado en el panel administrativo.
        boolean correoFueEnviado = caso.isCorreoEnviado() || caso.getFechaUltimoReporteSemanal() != null;
        boolean whatsappFueEnviado = caso.isWhatsappEnviado() || caso.getFechaUltimoReporteWhatsapp() != null;

        return new CasoAdminResponse(
                caso.getId(),
                caso.getFuente(),
                caso.getFuente().getNombreVisible(),
                caso.getNumeroCaso(),
                nombreMostrado,
                caso.getCliente().getCorreo(),
                caso.getCliente().getTelefono(),
                caso.getRadicadoId(),
                correoFueEnviado,
                whatsappFueEnviado,
                caso.getNotasInternas(),
                caso.getFechaCreacion(),
                caso.getFechaUltimoReporteSemanal(),
                caso.getFechaUltimoReporteWhatsapp(),
                caso.getIdProcesoCpnu() != null,
                caso.getFechaUltimoSeguimiento(),
                caso.getUltimoSeguimientoExitoso(),
                tieneActualizacionSinRevisar,
                fechaUltimaActuacionDetectada,
                fechaUltimoMovimientoJudicial,
                caso.getAbogadoAsignado() != null ? caso.getAbogadoAsignado().getId() : null,
                caso.getAbogadoAsignado() != null ? caso.getAbogadoAsignado().getNombre() : null,
                diasParaBorrarBoletin,
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
                caso.isRadicadoFormatoInvalido(),
                caso.isEsPrivadoCifrado(),
                caso.getMotivoConfidencialidad(),
                caso.isFinalizado(),
                caso.getFechaFinalizacion(),
                caso.getFinalizadoPor() != null ? caso.getFinalizadoPor().getNombre() : null
        );
    }
}
