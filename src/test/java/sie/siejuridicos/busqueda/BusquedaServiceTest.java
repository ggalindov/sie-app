package sie.siejuridicos.busqueda;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import sie.siejuridicos.caso.Caso;
import sie.siejuridicos.caso.CasoRepository;
import sie.siejuridicos.caso.Cliente;
import sie.siejuridicos.crm.ClienteCrm;
import sie.siejuridicos.crm.ClienteCrmRepository;
import sie.siejuridicos.crm.TipoClienteCrm;
import sie.siejuridicos.documento.DocumentoFirma;
import sie.siejuridicos.documento.DocumentoFirmaRepository;
import sie.siejuridicos.documento.DocumentoFirmanteRepository;
import sie.siejuridicos.solicitud.Solicitud;
import sie.siejuridicos.solicitud.SolicitudRepository;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// Buscador total del panel admin (pedido explícito del usuario: "buscar nombre... o por
// radicado... toda la búsqueda"). Lo central a probar: que el radicado (plano) se encuentra
// por SQL directo, que el nombre cifrado del CRM (ClienteCrm.nombre, CampoCifradoConverter)
// sigue siendo buscable porque el filtro corre en memoria sobre el valor ya descifrado por
// el converter al cargar la entidad, y que un texto demasiado corto no dispara ninguna
// consulta (mismo criterio de no cargar tablas completas por una sola tecla).
@ExtendWith(MockitoExtension.class)
class BusquedaServiceTest {

    @Mock private CasoRepository casoRepository;
    @Mock private ClienteCrmRepository clienteCrmRepository;
    @Mock private SolicitudRepository solicitudRepository;
    @Mock private DocumentoFirmaRepository documentoFirmaRepository;
    @Mock private DocumentoFirmanteRepository documentoFirmanteRepository;

    private BusquedaService servicio() {
        return new BusquedaService(casoRepository, clienteCrmRepository, solicitudRepository,
                documentoFirmaRepository, documentoFirmanteRepository);
    }

    @Test
    void encuentraPorRadicadoExactoSinCargarTodosLosCasos() {
        Caso caso = new Caso();
        caso.setRadicadoId("25513318400120250014700");
        caso.setNumeroCaso("INT-001");
        when(casoRepository.findByRadicadoIdContainingIgnoreCaseOrNumeroCasoContainingIgnoreCase(
                "255133", "255133")).thenReturn(List.of(caso));
        when(clienteCrmRepository.findAllByOrderByFechaCreacionDesc()).thenReturn(List.of());
        when(solicitudRepository.findByNombreContainingIgnoreCase(anyString())).thenReturn(List.of());
        when(documentoFirmaRepository.findByNombreContainingIgnoreCase(anyString())).thenReturn(List.of());
        when(documentoFirmanteRepository.findByNombreCapturadoContainingIgnoreCase(anyString())).thenReturn(List.of());

        List<ResultadoBusqueda> resultados = servicio().buscar("255133");

        assertTrue(resultados.stream().anyMatch(r -> r.tipo().equals("CASO") && r.titulo().equals("25513318400120250014700")));
        // Ya hubo match por radicado: no hace falta cargar+descifrar todos los casos.
        verify(casoRepository, never()).listarTodosConDetalle();
    }

    @Test
    void siNoHayMatchPorRadicadoBuscaPorNombreDelClienteDescifrado() {
        Cliente cliente = new Cliente();
        cliente.setNombre("Jorge Mario Cifuentes Lara");
        Caso caso = new Caso();
        caso.setCliente(cliente);
        when(casoRepository.findByRadicadoIdContainingIgnoreCaseOrNumeroCasoContainingIgnoreCase(
                anyString(), anyString())).thenReturn(List.of());
        when(casoRepository.listarTodosConDetalle()).thenReturn(List.of(caso));
        when(clienteCrmRepository.findAllByOrderByFechaCreacionDesc()).thenReturn(List.of());
        when(solicitudRepository.findByNombreContainingIgnoreCase(anyString())).thenReturn(List.of());
        when(documentoFirmaRepository.findByNombreContainingIgnoreCase(anyString())).thenReturn(List.of());
        when(documentoFirmanteRepository.findByNombreCapturadoContainingIgnoreCase(anyString())).thenReturn(List.of());

        List<ResultadoBusqueda> resultados = servicio().buscar("cifuentes");

        assertTrue(resultados.stream().anyMatch(r -> r.tipo().equals("CASO") && r.subtitulo().equals("Jorge Mario Cifuentes Lara")));
    }

    @Test
    void encuentraClienteCrmPorNombreYaDescifradoPorElConverter() {
        ClienteCrm cliente = new ClienteCrm();
        cliente.setNombre("Natalia Cárdenas Triviño");
        cliente.setTipo(TipoClienteCrm.PERSONA_NATURAL);
        when(casoRepository.findByRadicadoIdContainingIgnoreCaseOrNumeroCasoContainingIgnoreCase(
                anyString(), anyString())).thenReturn(List.of());
        when(casoRepository.listarTodosConDetalle()).thenReturn(List.of());
        when(clienteCrmRepository.findAllByOrderByFechaCreacionDesc()).thenReturn(List.of(cliente));
        when(solicitudRepository.findByNombreContainingIgnoreCase(anyString())).thenReturn(List.of());
        when(documentoFirmaRepository.findByNombreContainingIgnoreCase(anyString())).thenReturn(List.of());
        when(documentoFirmanteRepository.findByNombreCapturadoContainingIgnoreCase(anyString())).thenReturn(List.of());

        List<ResultadoBusqueda> resultados = servicio().buscar("natalia");

        assertEquals(1, resultados.stream().filter(r -> r.tipo().equals("CLIENTE")).count());
    }

    @Test
    void encuentraSolicitudPorNombre() {
        Solicitud solicitud = new Solicitud();
        solicitud.setNombre("Karen Yalena Cantillo Martínez");
        solicitud.setCorreo("karen@example.com");
        when(casoRepository.findByRadicadoIdContainingIgnoreCaseOrNumeroCasoContainingIgnoreCase(
                anyString(), anyString())).thenReturn(List.of());
        when(casoRepository.listarTodosConDetalle()).thenReturn(List.of());
        when(clienteCrmRepository.findAllByOrderByFechaCreacionDesc()).thenReturn(List.of());
        when(solicitudRepository.findByNombreContainingIgnoreCase("karen")).thenReturn(List.of(solicitud));
        when(documentoFirmaRepository.findByNombreContainingIgnoreCase(anyString())).thenReturn(List.of());
        when(documentoFirmanteRepository.findByNombreCapturadoContainingIgnoreCase(anyString())).thenReturn(List.of());

        List<ResultadoBusqueda> resultados = servicio().buscar("karen");

        assertTrue(resultados.stream().anyMatch(r -> r.tipo().equals("SOLICITUD") && r.titulo().equals("Karen Yalena Cantillo Martínez")));
    }

    @Test
    void encuentraDocumentoPorNombreDeDocumento() {
        DocumentoFirma documento = new DocumentoFirma(null, null, "Contrato de prestación de servicios.pdf", "application/pdf", null);
        // getId() es un @GeneratedValue que Hibernate solo asigna al persistir -- en este
        // test sin BD real, forzarlo por reflexión es la única forma de probar la parte del
        // servicio que sí lo usa (ResultadoBusqueda.id()).
        ReflectionTestUtils.setField(documento, "id", UUID.randomUUID());
        when(casoRepository.findByRadicadoIdContainingIgnoreCaseOrNumeroCasoContainingIgnoreCase(
                anyString(), anyString())).thenReturn(List.of());
        when(casoRepository.listarTodosConDetalle()).thenReturn(List.of());
        when(clienteCrmRepository.findAllByOrderByFechaCreacionDesc()).thenReturn(List.of());
        when(solicitudRepository.findByNombreContainingIgnoreCase(anyString())).thenReturn(List.of());
        when(documentoFirmaRepository.findByNombreContainingIgnoreCase("contrato")).thenReturn(List.of(documento));
        when(documentoFirmanteRepository.findByNombreCapturadoContainingIgnoreCase(anyString())).thenReturn(List.of());

        List<ResultadoBusqueda> resultados = servicio().buscar("contrato");

        assertTrue(resultados.stream().anyMatch(r -> r.tipo().equals("DOCUMENTO")));
    }

    @Test
    void queryDeUnSoloCaracterNoDisparaNingunaConsulta() {
        List<ResultadoBusqueda> resultados = servicio().buscar("a");

        assertEquals(List.of(), resultados);
        verifyNoInteractions(casoRepository, clienteCrmRepository, solicitudRepository,
                documentoFirmaRepository, documentoFirmanteRepository);
    }

    @Test
    void queryNuloNoDisparaNingunaConsulta() {
        List<ResultadoBusqueda> resultados = servicio().buscar(null);

        assertEquals(List.of(), resultados);
        verifyNoInteractions(casoRepository, clienteCrmRepository, solicitudRepository,
                documentoFirmaRepository, documentoFirmanteRepository);
    }
}
