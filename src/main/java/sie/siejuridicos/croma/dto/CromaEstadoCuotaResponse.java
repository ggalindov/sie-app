package sie.siejuridicos.croma.dto;

public record CromaEstadoCuotaResponse(
        String mesAno,
        int creditosConsumidos,
        int limiteCreditos,
        int creditosRestantes,
        int consultasRealizadas,
        int consultasRestantes,
        int creditosPorConsulta,
        boolean tieneApiKeyConfigurada,
        boolean cuotaDisponible,
        int keyActivaIndice,
        int totalKeysConfiguradas,
        int creditosConsumidosKeyActiva,
        int creditosPorKey
) {
}
