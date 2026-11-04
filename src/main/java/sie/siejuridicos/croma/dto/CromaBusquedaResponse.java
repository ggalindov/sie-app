package sie.siejuridicos.croma.dto;

import java.util.List;
import java.util.Map;

public record CromaBusquedaResponse(
        boolean exito,
        String mensaje,
        int totalCasos,
        List<CromaCasoDto> casos,
        int creditosConsumidosMes,
        int limiteCreditosMes,
        int consultasRestantesMes,
        String documentoIdentificado,
        String nombreResuelto,
        List<String> ramasConsultadas,
        Map<String, Integer> totalPorRama
) {
    public static CromaBusquedaResponse exito(
            List<CromaCasoDto> casos,
            int consumidos,
            int limite,
            int restantes,
            String documentoIdentificado,
            String nombreResuelto,
            List<String> ramasConsultadas,
            Map<String, Integer> totalPorRama) {
        String msg = casos.isEmpty()
                ? "No se encontraron procesos judiciales en ninguna de las ramas consultadas."
                : "Se encontraron " + casos.size() + " procesos judiciales en las ramas del poder consultadas.";
        return new CromaBusquedaResponse(
                true, msg, casos.size(), casos, consumidos, limite, restantes,
                documentoIdentificado, nombreResuelto, ramasConsultadas, totalPorRama);
    }

    public static CromaBusquedaResponse exito(List<CromaCasoDto> casos, int consumidos, int limite, int restantes) {
        return exito(casos, consumidos, limite, restantes, null, null,
                List.of("RAMA JUDICIAL ORDINARIA"), Map.of("RAMA JUDICIAL ORDINARIA", casos.size()));
    }

    public static CromaBusquedaResponse error(String mensaje, int consumidos, int limite, int restantes) {
        return new CromaBusquedaResponse(
                false, mensaje, 0, List.of(), consumidos, limite, restantes,
                null, null, List.of(), Map.of());
    }
}
