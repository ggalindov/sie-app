package sie.siejuridicos.vigilancia.dto;

import sie.siejuridicos.caso.Caso;
import sie.siejuridicos.vigilancia.BoletinJudicialCache;

import java.time.LocalDateTime;

// Pedido explícito del usuario: "el sistema debe de traer y darle la opcion de todos los
// casos de ver/descargar todos los boletines que esten disponibles en las ramas o donde
// esten" -- una fila de /admin/boletines (nueva pantalla, lista TODOS los boletines
// cacheados de TODOS los casos). Nunca se expone contenidoBytes aquí (sería enorme
// repetido en cada fila de la lista) -- para ver/descargar un boletín puntual, el frontend
// llama a GET /api/admin/vigilancia/boletines/{id}/pdf, que sirve el PDF ya cacheado
// directamente, sin volver a consultar Publicaciones Procesales en vivo (más rápido, y no
// depende de que el portal esté respondiendo en ese momento).
public record BoletinJudicialResponse(
        Long id,
        Long casoId,
        String nombreCliente,
        String radicadoId,
        String nombreArchivo,
        String urlOrigen,
        boolean coincideNombreActuacion,
        LocalDateTime fechaDescarga,
        LocalDateTime fechaExpiracion,
        LocalDateTime fechaEmision,
        String tipoActuacion
) {
    private static final java.util.regex.Pattern PATRON_FECHA_GUIONES =
            java.util.regex.Pattern.compile("(202[0-9])-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])");
    private static final java.util.regex.Pattern PATRON_FECHA_COMPACTA =
            java.util.regex.Pattern.compile("(202[0-9])(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])");

    public static BoletinJudicialResponse desde(BoletinJudicialCache b) {
        Caso caso = b.getCaso();
        String nombreMostrado = caso.getNombreEnHoja() != null
                ? caso.getNombreEnHoja()
                : (caso.getCliente() != null ? caso.getCliente().getNombre() : "Cliente");

        LocalDateTime fechaEmision = null;
        String tipoActuacion = null;
        if (b.getActuacion() != null) {
            fechaEmision = b.getActuacion().getFechaRegistro() != null
                    ? b.getActuacion().getFechaRegistro()
                    : b.getActuacion().getFechaActuacion();
            tipoActuacion = b.getActuacion().getNombreActuacion();
        }
        if (fechaEmision == null) {
            fechaEmision = extraerFechaDeNombreArchivo(b.getNombreArchivo());
        }
        if (fechaEmision == null) {
            fechaEmision = b.getFechaDescarga();
        }

        return new BoletinJudicialResponse(
                b.getId(),
                caso.getId(),
                nombreMostrado,
                caso.getRadicadoId(),
                b.getNombreArchivo(),
                b.getUrlOrigen(),
                b.isCoincideNombreActuacion(),
                b.getFechaDescarga(),
                b.getFechaExpiracion(),
                fechaEmision,
                tipoActuacion);
    }

    private static LocalDateTime extraerFechaDeNombreArchivo(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return null;
        }
        var mG = PATRON_FECHA_GUIONES.matcher(nombre);
        if (mG.find()) {
            try {
                return java.time.LocalDate.parse(mG.group(0)).atStartOfDay();
            } catch (Exception ignored) {}
        }
        var mC = PATRON_FECHA_COMPACTA.matcher(nombre);
        if (mC.find()) {
            try {
                String anio = mC.group(1);
                String mes = mC.group(2);
                String dia = mC.group(3);
                return java.time.LocalDate.of(Integer.parseInt(anio), Integer.parseInt(mes), Integer.parseInt(dia)).atStartOfDay();
            } catch (Exception ignored) {}
        }
        return null;
    }
}
