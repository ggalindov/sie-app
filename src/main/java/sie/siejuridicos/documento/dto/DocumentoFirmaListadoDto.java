package sie.siejuridicos.documento.dto;

import sie.siejuridicos.documento.DocumentoFirma;

import java.time.LocalDateTime;

public record DocumentoFirmaListadoDto(
        String id,
        String nombre,
        Long casoId,
        String creadoPorNombre,
        Long numeroSecuencia,
        String estadoDocumento,
        String estadoIntegridad,
        LocalDateTime fechaCreacion
) {
    public static DocumentoFirmaListadoDto from(DocumentoFirma d) {
        return new DocumentoFirmaListadoDto(
                d.getId().toString(),
                d.getNombre(),
                d.getCaso() != null ? d.getCaso().getId() : null,
                d.getCreadoPorStaff() != null ? d.getCreadoPorStaff().getNombre() : null,
                d.getNumeroSecuencia(),
                d.getEstadoDocumento(),
                d.getEstadoIntegridad(),
                d.getFechaCreacion()
        );
    }
}
