package sie.siejuridicos.crm.dto;

import java.math.BigDecimal;
import java.util.Map;

public record CrmDashboardResponse(
        long totalClientes,
        long prospectosActivos,
        long casosActivos,
        BigDecimal valorTotalPipeline,
        long cobrosAlDia,
        long cobrosPendientes,
        Map<String, Long> leadsPorEtapa
) {
}
