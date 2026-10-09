package sie.siejuridicos.crm.dto;

import java.util.List;

// El directorio de clientes CRM pasó de devolver siempre la lista completa (225+ clientes y
// creciendo, renderizados todos a la vez en el panel) a paginar en el backend (ver
// CrmService.listarClientes). El filtro por texto libre sigue necesitando leer todos los
// clientes en memoria porque nombre/correo/teléfono/cédula están cifrados en la base de
// datos (CampoCifradoConverter, no se puede hacer LIKE en SQL sobre texto cifrado) -- lo que
// sí se corrige es dejar de construir la ficha completa (2 consultas extra por cliente: casos
// y cobros) para los clientes que no van en la página pedida.
public record ClienteCrmPaginaResponse(
        List<ClienteCrmResponse> contenido,
        int pagina,
        int totalPaginas,
        long totalElementos
) {
}
