package sie.siejuridicos.documento.events;

import sie.siejuridicos.documento.DocumentoFirma;

// Publicado por DocumentIntegrityGuard la PRIMERA vez que un documento pasa a ALTERADO (no
// se repite en detecciones posteriores del mismo documento). Dispara una alerta a gerencia.
public record DocumentoAlteradoEvent(DocumentoFirma documento, String motivo) {
}
