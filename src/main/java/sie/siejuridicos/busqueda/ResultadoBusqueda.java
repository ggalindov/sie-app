package sie.siejuridicos.busqueda;

// Resultado uniforme del buscador total del panel (ver BusquedaService): cada módulo
// (casos, clientes, solicitudes, documentos) se reduce a esta misma forma para que el
// frontend pinte un solo dropdown agrupado por "tipo" sin tener que conocer la forma real
// de cada entidad. "ruta" ya trae la navegación completa que el frontend debe usar al hacer
// clic (ver BusquedaService para el criterio de cada una).
public record ResultadoBusqueda(String tipo, String id, String titulo, String subtitulo, String ruta) {
}
