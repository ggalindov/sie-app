package sie.siejuridicos.caso;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sie.siejuridicos.caso.dto.CasoConsultaResponse;
import sie.siejuridicos.common.cifrado.CifradoService;
import sie.siejuridicos.common.exception.RecursoNoEncontradoException;
import sie.siejuridicos.correo.EmailService;
import sie.siejuridicos.hojacalculo.HojaCalculoService;
import sie.siejuridicos.registro.RegistroSistemaService;
import sie.siejuridicos.registro.TipoRegistroSistema;
import sie.siejuridicos.whatsapp.WhatsAppService;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Pedido explícito del usuario: dejar en el Registro del Sistema qué radicado consultó su
// estado y cuándo, tanto si existe como si no -- ver CasoService.consultar(). Estas pruebas
// confirman que ESE registro se dispara correctamente en ambos casos, sin bloquear la
// respuesta real al visitante si el registro fallara (registrar() ya es responsabilidad de
// RegistroSistemaService, con su propia transacción independiente).
@ExtendWith(MockitoExtension.class)
class CasoServiceConsultaTest {

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

    private static Caso casoManual(String radicadoId) {
        Cliente cliente = new Cliente();
        cliente.setNombre("Cliente de prueba");
        Caso caso = new Caso();
        caso.setCliente(cliente);
        caso.setFuente(FuenteCaso.MANUAL);
        caso.setRadicadoId(radicadoId);
        return caso;
    }

    @Test
    void unaConsultaExitosaQuedaRegistradaConElRadicadoYLaFuente() {
        Caso caso = casoManual("RAD-100");
        when(casoRepository.findByRadicadoId("RAD-100")).thenReturn(Optional.of(caso));

        CasoConsultaResponse respuesta = crearServicio().consultar("RAD-100");

        assertEquals("RAD-100", respuesta.radicadoId());
        ArgumentCaptor<String> descripcion = ArgumentCaptor.forClass(String.class);
        verify(registroSistemaService).registrar(
                eq(TipoRegistroSistema.CONSULTA_ESTADO_CASO), descripcion.capture(), eq(true));
        assertTrue(descripcion.getValue().contains("RAD-100"));
        assertTrue(descripcion.getValue().contains(FuenteCaso.MANUAL.getNombreVisible()));
    }

    @Test
    void unaConsultaDeUnRadicadoInexistenteQuedaRegistradaComoNoExitosaYLanzaExcepcion() {
        when(casoRepository.findByRadicadoId("NO-EXISTE")).thenReturn(Optional.empty());

        CasoService servicio = crearServicio();
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.consultar("NO-EXISTE"));

        ArgumentCaptor<String> descripcion = ArgumentCaptor.forClass(String.class);
        verify(registroSistemaService).registrar(
                eq(TipoRegistroSistema.CONSULTA_ESTADO_CASO), descripcion.capture(), eq(false));
        assertTrue(descripcion.getValue().contains("NO-EXISTE"));
    }

    // Abuso/error de tipeo real (no un radicado real de la firma): un valor absurdamente
    // largo en el parámetro público "codigo" no debe generar una fila gigante en la
    // bitácora -- se trunca antes de guardarse, nunca antes de buscarlo en la base de datos.
    @Test
    void unRadicadoDemasiadoLargoSeTruncaEnElRegistroPeroSeBuscaCompleto() {
        String radicadoLargo = "X".repeat(500);
        when(casoRepository.findByRadicadoId(radicadoLargo)).thenReturn(Optional.empty());

        CasoService servicio = crearServicio();
        assertThrows(RecursoNoEncontradoException.class, () -> servicio.consultar(radicadoLargo));

        verify(casoRepository).findByRadicadoId(radicadoLargo);
        ArgumentCaptor<String> descripcion = ArgumentCaptor.forClass(String.class);
        verify(registroSistemaService).registrar(
                eq(TipoRegistroSistema.CONSULTA_ESTADO_CASO), descripcion.capture(), eq(false));
        assertTrue(descripcion.getValue().length() < radicadoLargo.length(),
                "El radicado en el registro debe quedar truncado, no completo.");
    }
}
