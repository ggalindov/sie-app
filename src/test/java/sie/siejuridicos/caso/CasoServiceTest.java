package sie.siejuridicos.caso;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sie.siejuridicos.caso.dto.ResumenEnvioCorreos;
import sie.siejuridicos.common.cifrado.CifradoService;
import sie.siejuridicos.correo.EmailService;
import sie.siejuridicos.hojacalculo.HojaCalculoService;
import sie.siejuridicos.registro.RegistroSistemaService;
import sie.siejuridicos.whatsapp.WhatsAppService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Auditoría de seguridad crítica (pedido explícito del usuario): el envío masivo de
// notificaciones de radicado NUNCA puede cruzar los datos de un cliente con los de otro. Este
// caso de uso real ya había causado un incidente (envío masivo mal throttled), así que estas
// pruebas verifican explícitamente, con varios clientes en el mismo lote, que cada uno recibe
// EXACTAMENTE su propio nombre/correo/teléfono/radicado -- nunca el de otro cliente del lote.
//
// Se usan mocks en vez de un contexto Spring/BD real: enviarCorreosPendientes() ya tiene una
// pausa deliberada entre cada envío (PAUSA_ENTRE_ENVIOS_MS), así que estas pruebas se
// mantienen deliberadamente pequeñas (2-3 clientes) para no alargar la suite más de lo
// necesario -- el objetivo es probar el cruce de datos, no el volumen.
@ExtendWith(MockitoExtension.class)
class CasoServiceTest {

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

    private static Caso casoDe(String nombre, String correo, String telefono, String radicado) {
        Cliente cliente = new Cliente();
        cliente.setNombre(nombre);
        cliente.setCorreo(correo);
        cliente.setTelefono(telefono);
        Caso caso = new Caso();
        caso.setCliente(cliente);
        caso.setFuente(FuenteCaso.JUDICIALES);
        caso.setRadicadoId(radicado);
        return caso;
    }

    @Test
    void cadaClienteRecibeSoloSuPropioNombreCorreoYRadicadoPorCorreo() {
        Caso casoA = casoDe("Ana Torres", "ana@correo-cliente-a.com", null, "RAD-AAA-001");
        Caso casoB = casoDe("Bruno Pérez", "bruno@correo-cliente-b.com", null, "RAD-BBB-002");
        Caso casoC = casoDe("Carla Ruiz", "carla@correo-cliente-c.com", null, "RAD-CCC-003");
        when(casoRepository.listarPendientesDeNotificacion()).thenReturn(List.of(casoA, casoB, casoC));
        when(emailService.enviarCodigoCasoSincrono(anyString(), anyString(), anyString())).thenReturn(true);

        crearServicio().enviarCorreosPendientes();

        // Verificación exacta y exhaustiva: cada tripleta (nombre, correo, radicado) debe
        // llegar junta, tal cual pertenece a SU cliente, ni una sola vez con un valor de otro.
        verify(emailService, times(1)).enviarCodigoCasoSincrono("Ana Torres", "ana@correo-cliente-a.com", "RAD-AAA-001");
        verify(emailService, times(1)).enviarCodigoCasoSincrono("Bruno Pérez", "bruno@correo-cliente-b.com", "RAD-BBB-002");
        verify(emailService, times(1)).enviarCodigoCasoSincrono("Carla Ruiz", "carla@correo-cliente-c.com", "RAD-CCC-003");
        // Ninguna combinación cruzada debe haberse dado jamás.
        verify(emailService, never()).enviarCodigoCasoSincrono(eq("Ana Torres"), anyString(), eq("RAD-BBB-002"));
        verify(emailService, never()).enviarCodigoCasoSincrono(eq("Ana Torres"), anyString(), eq("RAD-CCC-003"));
        verify(emailService, never()).enviarCodigoCasoSincrono(eq("Bruno Pérez"), anyString(), eq("RAD-AAA-001"));
        verify(emailService, never()).enviarCodigoCasoSincrono(eq("Bruno Pérez"), anyString(), eq("RAD-CCC-003"));
        verify(emailService, never()).enviarCodigoCasoSincrono(eq("Carla Ruiz"), anyString(), eq("RAD-AAA-001"));
        verify(emailService, never()).enviarCodigoCasoSincrono(eq("Carla Ruiz"), anyString(), eq("RAD-BBB-002"));
        verify(emailService, never()).enviarCodigoCasoSincrono(anyString(), eq("ana@correo-cliente-a.com"), eq("RAD-BBB-002"));
        verify(emailService, never()).enviarCodigoCasoSincrono(anyString(), eq("bruno@correo-cliente-b.com"), eq("RAD-AAA-001"));
    }

    @Test
    void cadaClienteRecibeSoloSuPropioNombreTelefonoYRadicadoPorWhatsapp() {
        Caso casoA = casoDe("Ana Torres", null, "3001112222", "RAD-AAA-001");
        Caso casoB = casoDe("Bruno Pérez", null, "3003334444", "RAD-BBB-002");
        when(casoRepository.listarPendientesDeNotificacion()).thenReturn(List.of(casoA, casoB));
        when(whatsAppService.isConfigurado()).thenReturn(true);
        when(whatsAppService.enviarCodigoCasoSincrono(anyString(), anyString(), anyString())).thenReturn(true);

        crearServicio().enviarCorreosPendientes();

        verify(whatsAppService, times(1)).enviarCodigoCasoSincrono("Ana Torres", "3001112222", "RAD-AAA-001");
        verify(whatsAppService, times(1)).enviarCodigoCasoSincrono("Bruno Pérez", "3003334444", "RAD-BBB-002");
        verify(whatsAppService, never()).enviarCodigoCasoSincrono(eq("Ana Torres"), anyString(), eq("RAD-BBB-002"));
        verify(whatsAppService, never()).enviarCodigoCasoSincrono(anyString(), eq("3003334444"), eq("RAD-AAA-001"));
        // Sin correo capturado en ninguno de los dos: el canal de correo nunca debió dispararse.
        verify(emailService, never()).enviarCodigoCasoSincrono(anyString(), anyString(), anyString());
    }

    // Reproduce con datos ficticios el incidente real que motivó el envío secuencial
    // (ver CasoService.PAUSA_ENTRE_ENVIOS_MS): confirma que un fallo de envío para un cliente
    // (ej. buzón inexistente) no deja "enviado=true" en su registro, y sobre todo que NO
    // afecta ni contamina el resultado de los demás clientes del mismo lote.
    @Test
    void unFalloDeEnvioParaUnClienteNoAfectaElResultadoDeLosDemas() {
        Caso casoA = casoDe("Ana Torres", "ana@correo-cliente-a.com", null, "RAD-AAA-001");
        Caso casoB = casoDe("Bruno Pérez", "bruno-invalido@", null, "RAD-BBB-002");
        Caso casoC = casoDe("Carla Ruiz", "carla@correo-cliente-c.com", null, "RAD-CCC-003");
        when(casoRepository.listarPendientesDeNotificacion()).thenReturn(List.of(casoA, casoB, casoC));
        when(emailService.enviarCodigoCasoSincrono(eq("Ana Torres"), anyString(), anyString())).thenReturn(true);
        when(emailService.enviarCodigoCasoSincrono(eq("Bruno Pérez"), anyString(), anyString())).thenReturn(false);
        when(emailService.enviarCodigoCasoSincrono(eq("Carla Ruiz"), anyString(), anyString())).thenReturn(true);

        ResumenEnvioCorreos resumen = crearServicio().enviarCorreosPendientes();

        assertEquals(2, resumen.correosEnviados());
        assertEquals(1, resumen.correosFallidos());
        // Cada cliente se guarda con SU PROPIO estado, capturado en el momento correcto del
        // bucle -- Ana y Carla deben quedar marcadas como enviadas, Bruno no.
        ArgumentCaptor<Caso> guardados = ArgumentCaptor.forClass(Caso.class);
        verify(casoRepository, times(3)).save(guardados.capture());
        for (Caso guardado : guardados.getAllValues()) {
            boolean esperaExito = !guardado.getCliente().getNombre().equals("Bruno Pérez");
            assertEquals(esperaExito, guardado.isCorreoEnviado(),
                    "El estado de envío de " + guardado.getCliente().getNombre() + " no debe verse afectado por los demás clientes del lote");
        }
    }

    @Test
    void unCasoSinRadicadoJamasDisparaUnEnvio() {
        Caso sinRadicado = casoDe("Sin Radicado", "correo@ejemplo.com", null, null);
        when(casoRepository.listarPendientesDeNotificacion()).thenReturn(List.of(sinRadicado));

        crearServicio().enviarCorreosPendientes();

        verify(emailService, never()).enviarCodigoCasoSincrono(anyString(), anyString(), anyString());
        verify(whatsAppService, never()).enviarCodigoCasoSincrono(anyString(), anyString(), anyString());
    }
}
