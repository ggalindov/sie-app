package sie.siejuridicos.caso;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sie.siejuridicos.caso.dto.ResumenSincronizacion;
import sie.siejuridicos.common.cifrado.CifradoService;
import sie.siejuridicos.correo.EmailService;
import sie.siejuridicos.hojacalculo.HojaCalculoService;
import sie.siejuridicos.hojacalculo.dto.FilaSincronizacionHoja;
import sie.siejuridicos.hojacalculo.dto.ResultadoSincronizacionHoja;
import sie.siejuridicos.registro.RegistroSistemaService;
import sie.siejuridicos.whatsapp.WhatsAppService;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Auditoría de seguridad crítica: sincronizarDesdeHoja() es donde se decide a qué Cliente le
// pertenece cada radicado antes de que enviarCorreosPendientes() lo notifique (ver
// CasoServiceTest, que ya cubre el envío en sí). Estas pruebas cubren la lógica previa, más
// difícil de razonar a simple vista: qué pasa cuando dos filas de la hoja reclaman el mismo
// radicado. Un radicado "robado" de un caso a otro aquí sería exactamente el tipo de cruce de
// información entre clientes que el usuario pidió descartar por completo.
@ExtendWith(MockitoExtension.class)
class CasoServiceSincronizacionTest {

    @Mock
    private CasoRepository casoRepository;
    @Mock
    private ClienteRepository clienteRepository;
    @Mock
    private EmailService emailService;
    @Mock
    private WhatsAppService whatsAppService;
    @Mock
    private HojaCalculoService hojaCalculoService;
    @Mock
    private CifradoService cifradoService;
    @Mock
    private RegistroSistemaService registroSistemaService;

    private CasoService crearServicio() {
        return new CasoService(casoRepository, clienteRepository, emailService, whatsAppService,
                hojaCalculoService, cifradoService, registroSistemaService);
    }

    private void mockearHashIdentidad() {
        // indiceCiego solo se usa aquí para identidad de Cliente por correo -- una función
        // identidad basta para estas pruebas, que no versan sobre deduplicación de clientes.
        when(cifradoService.indiceCiego(anyString())).thenAnswer(inv -> "hash:" + inv.getArgument(0));
    }

    private static Caso casoExistente(String numeroCaso, String radicadoId, boolean notificado) {
        Cliente cliente = new Cliente();
        cliente.setNombre("Cliente " + numeroCaso);
        cliente.setCorreo("cliente" + numeroCaso + "@correo.com");
        cliente.setCorreoHash("hash:cliente" + numeroCaso + "@correo.com");
        Caso caso = new Caso();
        caso.setCliente(cliente);
        caso.setFuente(FuenteCaso.JUDICIALES);
        caso.setNumeroCaso(numeroCaso);
        caso.setRadicadoId(radicadoId);
        caso.setCorreoEnviado(notificado);
        caso.setWhatsappEnviado(notificado);
        return caso;
    }

    // Caso real que motivó esta prueba (ver el comentario de CasoService.sincronizarDesdeHoja):
    // un radicado real de la hoja que YA pertenece a un caso existente (caso "10") no debe
    // poder asignarse también a un caso NUEVO ("20") solo porque una fila de la hoja lo repite
    // -- eso "robaría" el radicado del cliente original y el nuevo cliente terminaría viendo
    // el estado del proceso ajeno en /consulta-caso.
    @Test
    void unRadicadoYaAsignadoAUnCasoExistenteNuncaSeLeAsignaAUnCasoNuevo() {
        mockearHashIdentidad();
        Caso casoDiez = casoExistente("10", "RAD-100", true);

        FilaSincronizacionHoja filaNueva = new FilaSincronizacionHoja(
                FuenteCaso.JUDICIALES, "20", "RAD-100", "Otro Cliente", "otro@correo.com", null);
        when(hojaCalculoService.listarParaSincronizar())
                .thenReturn(new ResultadoSincronizacionHoja(List.of(filaNueva), List.of()));
        when(casoRepository.findByFuenteAndNumeroCaso(FuenteCaso.JUDICIALES, "20")).thenReturn(Optional.empty());
        when(casoRepository.existsByRadicadoId("RAD-100")).thenReturn(true);
        when(clienteRepository.findByCorreoHash(anyString())).thenReturn(Optional.empty());
        when(clienteRepository.save(any(Cliente.class))).thenAnswer(inv -> inv.getArgument(0));
        when(casoRepository.findByFuente(FuenteCaso.JUDICIALES)).thenReturn(List.of());

        ResumenSincronizacion resumen = crearServicio().sincronizarDesdeHoja();

        assertEquals(1, resumen.radicadosDuplicados(),
                "La fila del caso nuevo debe quedar marcada como radicado duplicado, no asignada.");
        assertEquals("RAD-100", casoDiez.getRadicadoId(),
                "El radicado del caso original (10) no debe alterarse por la fila duplicada.");

        ArgumentCaptor<Caso> guardado = ArgumentCaptor.forClass(Caso.class);
        verify(casoRepository).save(guardado.capture());
        assertNull(guardado.getValue().getRadicadoId(),
                "El caso nuevo (20) debe crearse SIN radicado -- nunca con uno que ya pertenece a otro cliente.");

        // Ni el correo ni el WhatsApp deben dispararse desde la sincronización misma (eso lo
        // decide el admin aparte, ver enviarCorreosPendientes) ni, sobre todo, con el radicado
        // ajeno.
        verify(emailService, never()).enviarCodigoCaso(anyString(), anyString(), anyString());
        verify(whatsAppService, never()).enviarCodigoCaso(anyString(), anyString(), anyString());
    }

    // Bug real ya corregido (ver el comentario "esto es justo lo que causaba el bug
    // reportado" en CasoService): re-leer en una sincronización posterior el MISMO radicado ya
    // asignado al mismo caso NO debe tratarse como un duplicado consigo mismo, ni debe resetear
    // los indicadores de notificación -- si lo hiciera, el cliente recibiría de nuevo el mismo
    // radicado que ya se le había enviado, cada vez que se sincroniza.
    @Test
    void reconfirmarElMismoRadicadoEnUnaSincronizacionPosteriorNoSeMarcaComoDuplicadoNiReenviaNada() {
        mockearHashIdentidad();
        Caso caso = casoExistente("10", "RAD-100", true);

        FilaSincronizacionHoja filaReconfirmada = new FilaSincronizacionHoja(
                FuenteCaso.JUDICIALES, "10", "RAD-100", "Cliente 10", "cliente10@correo.com", null);
        when(hojaCalculoService.listarParaSincronizar())
                .thenReturn(new ResultadoSincronizacionHoja(List.of(filaReconfirmada), List.of()));
        when(casoRepository.findByFuenteAndNumeroCaso(FuenteCaso.JUDICIALES, "10")).thenReturn(Optional.of(caso));
        when(casoRepository.countByCliente(caso.getCliente())).thenReturn(1L);
        when(casoRepository.findByFuente(FuenteCaso.JUDICIALES)).thenReturn(List.of(caso));

        ResumenSincronizacion resumen = crearServicio().sincronizarDesdeHoja();

        assertEquals(0, resumen.radicadosDuplicados(),
                "Reconfirmar el mismo radicado del propio caso no es un duplicado.");
        assertTrue(caso.isCorreoEnviado(), "No debe resetearse: el radicado no cambió de verdad.");
        assertTrue(caso.isWhatsappEnviado(), "No debe resetearse: el radicado no cambió de verdad.");
        assertEquals("RAD-100", caso.getRadicadoId());
    }

    // Complemento del caso anterior: si el radicado SÍ cambió de verdad (corrección real del
    // despacho, no solo una relectura), los indicadores de notificación deben resetearse para
    // que al cliente se le avise del radicado correcto -- y solo cuando el nuevo valor está
    // libre (no pertenece a otro caso).
    @Test
    void unRadicadoQueDeVerdadCambioResetaLosIndicadoresDeNotificacion() {
        mockearHashIdentidad();
        Caso caso = casoExistente("10", "RAD-100-VIEJO", true);

        FilaSincronizacionHoja filaCorregida = new FilaSincronizacionHoja(
                FuenteCaso.JUDICIALES, "10", "RAD-100-NUEVO", "Cliente 10", "cliente10@correo.com", null);
        when(hojaCalculoService.listarParaSincronizar())
                .thenReturn(new ResultadoSincronizacionHoja(List.of(filaCorregida), List.of()));
        when(casoRepository.findByFuenteAndNumeroCaso(FuenteCaso.JUDICIALES, "10")).thenReturn(Optional.of(caso));
        when(casoRepository.findByRadicadoId("RAD-100-NUEVO")).thenReturn(Optional.empty());
        when(casoRepository.countByCliente(caso.getCliente())).thenReturn(1L);
        when(casoRepository.findByFuente(FuenteCaso.JUDICIALES)).thenReturn(List.of(caso));

        ResumenSincronizacion resumen = crearServicio().sincronizarDesdeHoja();

        assertEquals(0, resumen.radicadosDuplicados());
        assertEquals(1, resumen.casosActualizados());
        assertEquals("RAD-100-NUEVO", caso.getRadicadoId());
        assertFalse(caso.isCorreoEnviado(), "El radicado cambió de verdad: hay que volver a notificar.");
        assertFalse(caso.isWhatsappEnviado(), "El radicado cambió de verdad: hay que volver a notificar.");
    }
}
