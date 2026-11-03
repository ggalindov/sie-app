package sie.siejuridicos.croma.dto;

import java.util.List;

public record CromaBusquedaResponse(
        boolean exito,
        String mensaje,
        int totalCasos,
        List<CromaCasoDto> casos,
        int creditosConsumidosMes,
        int limiteCreditosMes,
        int consultasRestantesMes
) {
    public static CromaBusquedaResponse exito(List<CromaCasoDto> casos, int consumidos, int limite, int restantes) {
        String msg = casos.isEmpty()
                ? "No se encontraron procesos judiciales asociados a ese nombre en la Rama Judicial."
                : "Se encontraron " + casos.size() + " procesos judiciales en la Rama Judicial.";
        return new CromaBusquedaResponse(true, msg, casos.size(), casos, consumidos, limite, restantes);
    }

    public static CromaBusquedaResponse error(String mensaje, int consumidos, int limite, int restantes) {
        return new CromaBusquedaResponse(false, mensaje, 0, List.of(), consumidos, limite, restantes);
    }
}
