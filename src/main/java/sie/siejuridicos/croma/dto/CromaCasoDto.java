package sie.siejuridicos.croma.dto;

import java.time.LocalDate;

public record CromaCasoDto(
        String radicado,
        String despacho,
        String departamento,
        LocalDate fechaInicio,
        LocalDate fechaUltimaActuacion,
        String partes,
        boolean esPrivado,
        String rama,
        String fuente,
        boolean yaExisteEnSie,
        Long casoId,
        String claseProceso
) {
    // Constructor de compatibilidad hacia atrás
    public CromaCasoDto(
            String radicado,
            String despacho,
            String departamento,
            LocalDate fechaInicio,
            LocalDate fechaUltimaActuacion,
            String partes,
            boolean esPrivado) {
        this(radicado, despacho, departamento, fechaInicio, fechaUltimaActuacion, partes, esPrivado,
                inferirRama(despacho, null), "Rama Judicial (Croma)", false, null, null);
    }

    public static String inferirRama(String despacho, String clase) {
        String d = (despacho == null ? "" : despacho).toUpperCase();
        String c = (clase == null ? "" : clase).toUpperCase();

        if (d.contains("CONSEJO DE ESTADO") || d.contains("ADMINISTRATIVO") || d.contains("TRIBUNAL ADMINISTRATIVO")
                || d.startsWith("1100103") || d.startsWith("1100133") || d.startsWith("2500023")) {
            return "CONTENCIOSO-ADMINISTRATIVO (SAMAI)";
        }
        if (c.contains("TUTELA") || d.contains("CONSTITUCIONAL") || c.contains("ACCION DE TUTELA") || c.contains("ACCIONES DE TUTELA")) {
            return "CONSTITUCIONAL";
        }
        if (d.contains("SUPERINTENDENCIA") || d.contains("SUPERSOCIEDADES") || d.contains("SIC")) {
            return "SUPERINTENDENCIA";
        }
        return "RAMA JUDICIAL ORDINARIA";
    }
}
