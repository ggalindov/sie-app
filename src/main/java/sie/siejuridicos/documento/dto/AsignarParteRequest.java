package sie.siejuridicos.documento.dto;

// Una parte a asignar como firmante de un documento nuevo: clienteCrmId referencia un
// contacto ya existente en la biblioteca de contactos (ver crm.ClienteCrm -- se crea ahí
// mismo, vía POST /api/admin/crm/clientes, antes de armar esta solicitud). canal es
// "EMAIL" o "WHATSAPP"; se valida en el servidor que una EMPRESA solo pueda recibir
// "EMAIL" (ver DocumentoFirmaService.ParteAFirmar).
public record AsignarParteRequest(Long clienteCrmId, String canal) {
}
