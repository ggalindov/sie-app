package sie.siejuridicos.documento.events;

// Publicado cuando un usuario interno (gerente o abogado) sube o reemplaza su firma
// manuscrita desde "Mi firma" en su perfil (ver UsuarioInternoController). Dispara
// DocumentoFirmaStaffRetryListener, que reintenta cerrar la compuerta de firma de staff de
// cualquier documento que haya quedado EMITIDO esperando justo esta firma.
public record FirmaStaffActualizadaEvent(Long staffId) {
}
