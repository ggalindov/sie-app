package sie.siejuridicos.articulo.dto;

// URL pública absoluta lista para guardarse tal cual en CrearArticuloRequest.imagenUrl /
// ActualizarArticuloRequest.imagenUrl (esos DTOs exigen que empiece por http(s)://).
public record ImagenSubidaResponse(String url) {
}
