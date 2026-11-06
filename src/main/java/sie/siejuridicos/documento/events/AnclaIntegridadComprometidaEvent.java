package sie.siejuridicos.documento.events;

import java.time.LocalDate;

// Publicado por AnclaDiariaIntegridadScheduler.reverificarAnclasAnteriores() cuando el hash
// agregado recalculado de un día pasado ya no coincide con el que se selló externamente con
// RFC 3161 ese mismo día -- evidencia de que documento_eventos_checkpoints fue modificado
// directamente en la base de datos DESPUÉS del sello, sin pasar por ningún endpoint de la
// aplicación. A diferencia de DocumentoAlteradoEvent (un documento puntual), este es un aviso
// a nivel de todo el sistema: no identifica un documento específico porque el ancla solo
// prueba el AGREGADO de ese día, no cuál checkpoint individual cambió.
public record AnclaIntegridadComprometidaEvent(LocalDate fecha, String detalle) {
}
