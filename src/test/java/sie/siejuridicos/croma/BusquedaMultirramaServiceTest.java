package sie.siejuridicos.croma;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sie.siejuridicos.caso.Caso;
import sie.siejuridicos.caso.CasoRepository;
import sie.siejuridicos.common.cifrado.CifradoService;
import sie.siejuridicos.crm.ClienteCrm;
import sie.siejuridicos.crm.ClienteCrmRepository;
import sie.siejuridicos.croma.dto.CromaBusquedaRequest;
import sie.siejuridicos.croma.dto.CromaBusquedaResponse;
import sie.siejuridicos.croma.dto.CromaCasoDto;
import sie.siejuridicos.croma.dto.CromaEstadoCuotaResponse;
import sie.siejuridicos.vigilancia.RamaJudicialCpnuClient;
import sie.siejuridicos.vigilancia.SamaiClient;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusquedaMultirramaServiceTest {

    @Mock private CromaClient cromaClient;
    @Mock private CromaCreditosService creditosService;
    @Mock private RamaJudicialCpnuClient cpnuClient;
    @Mock private SamaiClient samaiClient;
    @Mock private ClienteCrmRepository clienteCrmRepository;
    @Mock private CasoRepository casoRepository;
    @Mock private CifradoService cifradoService;

    @InjectMocks
    private BusquedaMultirramaService service;

    @Test
    void inferirRama_detectaCorrectamenteCadaJurisdiccion() {
        assertEquals("CONTENCIOSO-ADMINISTRATIVO (SAMAI)",
                CromaCasoDto.inferirRama("CONSEJO DE ESTADO SALA DE LO CONTENCIOSO", null));
        assertEquals("CONTENCIOSO-ADMINISTRATIVO (SAMAI)",
                CromaCasoDto.inferirRama("TRIBUNAL ADMINISTRATIVO DE CUNDINAMARCA", null));
        assertEquals("CONTENCIOSO-ADMINISTRATIVO (SAMAI)",
                CromaCasoDto.inferirRama("110010327000", null));
        assertEquals("CONSTITUCIONAL",
                CromaCasoDto.inferirRama("JUZGADO 001 CIVIL", "ACCIONES DE TUTELA"));
        assertEquals("SUPERINTENDENCIA",
                CromaCasoDto.inferirRama("SUPERINTENDENCIA DE SOCIEDADES BOGOTA", null));
        assertEquals("RAMA JUDICIAL ORDINARIA",
                CromaCasoDto.inferirRama("JUZGADO 020 CIVIL MUNICIPAL DE BOGOTA", "Ejecutivo"));
    }

    @Test
    void buscar_conCedulaResueltaEnCrm_consultaTodasLasRamas() {
        when(creditosService.obtenerEstadoCuota()).thenReturn(new CromaEstadoCuotaResponse(
                "2026-11", 50, 4500, 4450, 5, 445, 10, true, true, 1, 3, 50, 1500));
        when(creditosService.tieneApiKeyConfigurada()).thenReturn(true);

        when(cifradoService.indiceCiego("1020304050")).thenReturn("hash-1020304050");
        ClienteCrm crmCliente = new ClienteCrm();
        crmCliente.setNombre("Carlos Alberto Gomez");
        crmCliente.setCedulaNit("1020304050");
        when(clienteCrmRepository.findByCedulaNitHash("hash-1020304050")).thenReturn(Optional.of(crmCliente));

        CromaCasoDto casoCroma = new CromaCasoDto(
                "11001400302020250115600",
                "Juzgado 020 Civil Municipal",
                "Bogotá",
                LocalDate.of(2025, 1, 15),
                LocalDate.of(2026, 10, 30),
                "Carlos Alberto Gomez vs Banco X",
                false,
                "RAMA JUDICIAL ORDINARIA",
                "Croma API",
                false,
                null,
                "Ejecutivo"
        );
        when(cromaClient.buscarProcesosPorEntidad(any())).thenReturn(
                CromaBusquedaResponse.exito(List.of(casoCroma), 50, 4500, 445));

        SamaiClient.ProcesoSamaiDto casoSamai = new SamaiClient.ProcesoSamaiDto(
                "11001031500020260873000",
                "Consejo de Estado Seccion Tercera",
                "Carlos Alberto Gomez",
                "Nacion - Ministerio",
                "Reparacion directa",
                LocalDate.of(2026, 8, 10),
                "Magistrado Ponente",
                "guid-123",
                false
        );
        when(samaiClient.pareceCaido()).thenReturn(false);
        when(samaiClient.buscarPorParte(anyString(), any())).thenReturn(List.of(casoSamai));

        when(cpnuClient.pareceCaido()).thenReturn(true);
        when(casoRepository.listarTodosConDetalle()).thenReturn(List.of());

        CromaBusquedaRequest req = new CromaBusquedaRequest("1020304050", "natural", false, null, null, null);
        CromaBusquedaResponse response = service.buscar(req);

        assertTrue(response.exito());
        assertEquals("1020304050", response.documentoIdentificado());
        assertEquals("Carlos Alberto Gomez", response.nombreResuelto());
        assertEquals(2, response.totalCasos());
        assertNotNull(response.totalPorRama());
        assertTrue(response.totalPorRama().containsKey("RAMA JUDICIAL ORDINARIA"));
        assertTrue(response.totalPorRama().containsKey("CONTENCIOSO-ADMINISTRATIVO (SAMAI)"));
    }
}
