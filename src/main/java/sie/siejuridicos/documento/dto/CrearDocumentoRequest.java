package sie.siejuridicos.documento.dto;

import java.util.List;

// Cuerpo "lógico" de POST /api/admin/documentos -- en la práctica llega como multipart
// (archivo + estos campos sueltos, ver DocumentoAdminController), este record solo agrupa
// lo que el controlador ya validó/parseó antes de llamar al servicio.
public record CrearDocumentoRequest(
        String nombre,
        Long casoId,
        List<Long> abogadoIds,
        List<AsignarParteRequest> partes
) {
}
