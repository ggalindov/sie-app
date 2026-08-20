package sie.siejuridicos.caso;

// Etapas genéricas de proceso legal (aplican a cualquier área de práctica), en el orden
// esperado de avance. El abogado/admin las actualiza manualmente desde el panel; no hay
// transición automática ni restricción de "no retroceder" (a diferencia de
// EstadoSolicitud), porque un caso puede volver a una etapa anterior en la práctica real.
public enum EtapaCaso {
    RADICADO, EN_ESTUDIO, EN_TRAMITE, AUDIENCIA_DILIGENCIA, RESUELTO
}
