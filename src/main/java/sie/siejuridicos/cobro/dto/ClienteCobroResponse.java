package sie.siejuridicos.cobro.dto;

import sie.siejuridicos.cobro.ClienteCobro;
import sie.siejuridicos.cobro.TipoClienteCobro;

import java.time.LocalDateTime;

// Vista de un cliente con cobro pendiente para el panel administrativo. A diferencia de
// CasoAdminResponse no hace falta ocultar el correo (aquí no hay una consulta pública
// equivalente: todo este módulo es exclusivamente de uso interno, ver CobroAdminController).
public record ClienteCobroResponse(
        Long id,
        TipoClienteCobro tipo,
        String tipoVisible,
        String numeroFila,
        String nombre,
        String correo,
        String telefono,
        String cedulaNit,
        String honorarios,
        Boolean pagoEsteMes,
        String respondioMensaje,
        LocalDateTime fechaUltimoRecordatorio,
        LocalDateTime fechaCreacion
) {
    public static ClienteCobroResponse desde(ClienteCobro cliente) {
        return new ClienteCobroResponse(
                cliente.getId(),
                cliente.getTipo(),
                cliente.getTipo().getNombreVisible(),
                cliente.getNumeroFila(),
                cliente.getNombre(),
                cliente.getCorreo(),
                cliente.getTelefono(),
                cliente.getCedulaNit(),
                cliente.getHonorarios(),
                cliente.getPagoEsteMes(),
                cliente.getRespondioMensaje(),
                cliente.getFechaUltimoRecordatorio(),
                cliente.getFechaCreacion()
        );
    }
}
