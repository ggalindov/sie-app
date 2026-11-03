package sie.siejuridicos.caso.dto;

// Edicion manual de un caso desde el panel (pedido explicito del usuario: "una variable para
// editar los casos, la idea es desligarnos del excel... un boton para editar caso para
// administrador unicamente y asignacion de caso por abogado", ver CasoAdminController y
// CasoService.actualizar()). No es un PATCH parcial real: el formulario del frontend siempre
// manda TODOS los campos (precargados con el valor actual), asi que cada uno reemplaza al
// que habia -- evita la ambiguedad de "null significa no tocar" vs "null significa borrar".
// numeroCaso NO es editable aqui a proposito: es la llave que usa la sincronizacion
// automatica para emparejar cada fila de la hoja con su Caso local (ver
// CasoRepository.findByFuenteAndNumeroCaso) -- cambiarla a mano romperia ese emparejamiento
// en la proxima sincronizacion.
public record ActualizarCasoRequest(
        String nombreEnHoja,
        String correoCliente,
        String telefonoCliente,
        String radicadoId,
        String notasInternas,
        Long abogadoAsignadoId
) {
}
