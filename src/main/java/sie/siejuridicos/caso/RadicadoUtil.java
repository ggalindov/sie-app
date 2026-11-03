package sie.siejuridicos.caso;

import java.util.regex.Pattern;

// Punto único de normalización del radicado judicial -- antes esta misma limpieza (quitar
// espacios/guiones/puntos internos) vivía duplicada en 3 sitios distintos (CasoService.
// consultar(), RamaJudicialCpnuClient, PublicacionesProcesalesClient), cada uno con su propia
// variante de regex -- bug real confirmado en vivo el 2026-11-01: 13 de 328 casos tenían un
// radicado con espacios internos ("11001 31 03 048 2026 00324 00") que SOLO se limpiaba en
// algunos de esos 3 sitios, nunca en el dato guardado en Caso.radicadoId -- así que el mismo
// caso podía funcionar en un cliente y fallar en otro, dependiendo de si ese cliente ya tenía
// el fix aplicado. Pedido explícito del usuario: "setea todos los radicados a radicados
// funcionales" -- ahora Caso.setRadicadoId() normaliza con ESTA clase antes de guardar (ver
// migración V72 para el backfill de los que ya estaban guardados sucios).
public final class RadicadoUtil {

    private static final Pattern RUIDO_INTERNO = Pattern.compile("[\\s\\-.]+");
    private static final Pattern SOLO_DIGITOS = Pattern.compile("\\d+");
    public static final int LONGITUD_RADICADO_CPNU = 23;

    private RadicadoUtil() {
    }

    // Quita espacios/guiones/puntos de CUALQUIER posición (no solo inicio/final, ese era el
    // bug real: .strip() nunca tocaba los internos). Nunca null -- "" si la entrada es null.
    public static String limpiar(String bruto) {
        return bruto == null ? "" : RUIDO_INTERNO.matcher(bruto).replaceAll("");
    }

    // true si, tras limpiar, el resultado son EXACTAMENTE 23 dígitos -- el único formato real
    // que el CPNU/Publicaciones Procesales puede resolver. Un radicado de otra jurisdicción
    // (comisaría de familia, inspección de policía, p. ej. "VIF 428-2026...") nunca lo cumple,
    // y eso es CORRECTO, no un error: esos casos legítimamente no tienen radicado CPNU.
    public static boolean esFormatoCpnuValido(String bruto) {
        String limpio = limpiar(bruto);
        return limpio.length() == LONGITUD_RADICADO_CPNU && SOLO_DIGITOS.matcher(limpio).matches();
    }

    // Para GUARDAR en Caso.radicadoId (ver su setter): si el dato bruto, una vez limpio, SÍ es
    // un radicado CPNU real (solo tenía ruido de formato), se guarda la versión limpia -- "lo
    // funcional". Si no lo es (otra jurisdicción, o dato genuinamente corrupto), se guarda el
    // valor original tal cual lo escribió/trajo la hoja -- nunca se mangla un identificador
    // que no es un radicado CPNU, eso rompería lo que se le muestra/envía al cliente (su
    // "VIF 428-2026" dejaría de ser legible si le quitamos los espacios a la fuerza).
    public static String normalizarParaAlmacenar(String bruto) {
        if (bruto == null) {
            return null;
        }
        String sinBordes = bruto.strip();
        if (sinBordes.isEmpty()) {
            return null;
        }
        String limpio = limpiar(sinBordes);
        return esFormatoCpnuValido(limpio) ? limpio : sinBordes;
    }
}
