package sie.siejuridicos.documento.dto;

import sie.siejuridicos.documento.DocumentoEvento;

import java.time.LocalDateTime;

public record DocumentoEventoDto(
        Long secuencia,
        String tipoEvento,
        String actorTipo,
        String actorNombre,
        String descripcion,
        String ip,
        String hashDocumentoEnEvento,
        LocalDateTime ocurridoEn
) {
    public static DocumentoEventoDto from(DocumentoEvento evento) {
        String actorNombre = evento.getStaff() != null
                ? evento.getStaff().getNombre()
                : (evento.getFirmante() != null ? evento.getFirmante().getNombreCapturado() : null);
        return new DocumentoEventoDto(evento.getSecuencia(), evento.getTipoEvento(), evento.getActorTipo(), actorNombre,
                evento.getDescripcion(), evento.getIp(), evento.getHashDocumentoEnEvento(), evento.getOcurridoEn());
    }
}
