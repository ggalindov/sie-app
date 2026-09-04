package sie.siejuridicos.cobro;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sie.siejuridicos.cobro.dto.ResumenEnvioRecordatoriosCobros;
import sie.siejuridicos.common.cifrado.CifradoService;
import sie.siejuridicos.correo.EmailService;
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

// Auditoría de seguridad crítica (pedido explícito del usuario): el recordatorio mensual de
// cobro es información financiera de un cliente real (monto de honorarios). Estas pruebas
// verifican, con varios clientes en el mismo lote de envío, que cada uno recibe EXACTAMENTE
// su propio nombre/correo/teléfono/monto -- nunca el de otro cliente, y que un cliente que
// debe saltarse (sin costo, ya pagó, o ya notificado este mes) nunca "roba" el turno de otro
// ni deja rastro en su resultado.
@ExtendWith(MockitoExtension.class)
class CobroServiceTest {

    @Mock
    private ClienteCobroRepository clienteCobroRepository;
    @Mock
    private HojaCobrosService hojaCobrosService;
    @Mock
    private EmailService emailService;
    @Mock
    private WhatsAppService whatsAppService;
    @Mock
    private CifradoService cifradoService;
    @Mock
    private RegistroSistemaService registroSistemaService;

    private CobroService crearServicio() {
        return new CobroService(clienteCobroRepository, hojaCobrosService, emailService, whatsAppService,
                cifradoService, registroSistemaService);
    }

    private static ClienteCobro clienteDe(String numeroFila, String nombre, String correo, String telefono, String honorarios) {
        ClienteCobro cliente = new ClienteCobro();
        cliente.setTipo(TipoClienteCobro.EMPRESA);
        cliente.setNumeroFila(numeroFila);
        cliente.setNombre(nombre);
        cliente.setCorreo(correo);
        cliente.setTelefono(telefono);
        cliente.setHonorarios(honorarios);
        cliente.setActivo(true);
        return cliente;
    }

    @Test
    void cadaClienteRecibeSoloSuPropioNombreCorreoYMontoPorCorreo() {
        ClienteCobro a = clienteDe("1", "Empresa Alfa S.A.S.", "pagos@alfa.com", null, "$ 1.000.000");
        ClienteCobro b = clienteDe("2", "Empresa Beta Ltda.", "pagos@beta.com", null, "$ 2.500.000");
        ClienteCobro c = clienteDe("3", "Empresa Gamma S.A.", "pagos@gamma.com", null, "$ 750.000");
        when(clienteCobroRepository.findByActivoTrueOrderByNombreAsc()).thenReturn(List.of(a, b, c));
        when(emailService.enviarTirillaCobroSincrono(anyString(), anyString(), anyString())).thenReturn(true);

        crearServicio().enviarRecordatorios();

        verify(emailService, times(1)).enviarTirillaCobroSincrono("Empresa Alfa S.A.S.", "pagos@alfa.com", "$ 1.000.000");
        verify(emailService, times(1)).enviarTirillaCobroSincrono("Empresa Beta Ltda.", "pagos@beta.com", "$ 2.500.000");
        verify(emailService, times(1)).enviarTirillaCobroSincrono("Empresa Gamma S.A.", "pagos@gamma.com", "$ 750.000");
        // Ningún monto ni correo debió cruzarse jamás entre clientes distintos.
        verify(emailService, never()).enviarTirillaCobroSincrono(eq("Empresa Alfa S.A.S."), anyString(), eq("$ 2.500.000"));
        verify(emailService, never()).enviarTirillaCobroSincrono(eq("Empresa Beta Ltda."), anyString(), eq("$ 1.000.000"));
        verify(emailService, never()).enviarTirillaCobroSincrono(anyString(), eq("pagos@alfa.com"), eq("$ 2.500.000"));
        verify(emailService, never()).enviarTirillaCobroSincrono(anyString(), eq("pagos@beta.com"), eq("$ 750.000"));
    }

    @Test
    void cadaClienteRecibeSoloSuPropioNombreTelefonoYMontoPorWhatsapp() {
        ClienteCobro a = clienteDe("1", "Empresa Alfa S.A.S.", null, "3001112222", "$ 1.000.000");
        ClienteCobro b = clienteDe("2", "Empresa Beta Ltda.", null, "3003334444", "$ 2.500.000");
        when(clienteCobroRepository.findByActivoTrueOrderByNombreAsc()).thenReturn(List.of(a, b));
        when(whatsAppService.isConfigurado()).thenReturn(true);
        when(whatsAppService.enviarRecordatorioCobroSincrono(anyString(), anyString(), anyString())).thenReturn(true);

        crearServicio().enviarRecordatorios();

        verify(whatsAppService, times(1)).enviarRecordatorioCobroSincrono("Empresa Alfa S.A.S.", "3001112222", "$ 1.000.000");
        verify(whatsAppService, times(1)).enviarRecordatorioCobroSincrono("Empresa Beta Ltda.", "3003334444", "$ 2.500.000");
        verify(whatsAppService, never()).enviarRecordatorioCobroSincrono(eq("Empresa Alfa S.A.S."), anyString(), eq("$ 2.500.000"));
        verify(whatsAppService, never()).enviarRecordatorioCobroSincrono(anyString(), eq("3003334444"), eq("$ 1.000.000"));
    }

    // Pedido explícito del usuario ("todo cliente que tenga 0 en casilla de honorario
    // saltarlo"): confirma que saltarse a un cliente sin costo no interrumpe ni contamina el
    // envío de los demás clientes del mismo lote, y que ese cliente jamás recibe un correo/
    // WhatsApp de todas formas.
    @Test
    void unClienteSinCostoSeSaltaSinAfectarALosDemas() {
        ClienteCobro sinCosto = clienteDe("1", "Sin Costo S.A.S.", "correo@sincosto.com", null, "$ 0");
        ClienteCobro conCosto = clienteDe("2", "Con Costo Ltda.", "correo@concosto.com", null, "$ 500.000");
        when(clienteCobroRepository.findByActivoTrueOrderByNombreAsc()).thenReturn(List.of(sinCosto, conCosto));
        when(emailService.enviarTirillaCobroSincrono(anyString(), anyString(), anyString())).thenReturn(true);

        ResumenEnvioRecordatoriosCobros resumen = crearServicio().enviarRecordatorios();

        assertEquals(1, resumen.clientesSinCosto());
        assertEquals(1, resumen.correosEnviados());
        verify(emailService, never()).enviarTirillaCobroSincrono(eq("Sin Costo S.A.S."), anyString(), anyString());
        verify(emailService, times(1)).enviarTirillaCobroSincrono("Con Costo Ltda.", "correo@concosto.com", "$ 500.000");
    }

    // Pedido explícito del usuario: un cliente que ya pagó este mes no debe recibir el
    // recordatorio -- y, otra vez, eso no puede afectar a quien sí debe recibirlo en el mismo
    // lote.
    @Test
    void unClienteQueYaPagoEsteMesSeSaltaSinAfectarALosDemas() {
        ClienteCobro yaPago = clienteDe("1", "Ya Pagó S.A.S.", "correo@yapago.com", null, "$ 500.000");
        yaPago.setPagoEsteMes(true);
        ClienteCobro pendiente = clienteDe("2", "Pendiente Ltda.", "correo@pendiente.com", null, "$ 500.000");
        when(clienteCobroRepository.findByActivoTrueOrderByNombreAsc()).thenReturn(List.of(yaPago, pendiente));
        when(emailService.enviarTirillaCobroSincrono(anyString(), anyString(), anyString())).thenReturn(true);

        crearServicio().enviarRecordatorios();

        verify(emailService, never()).enviarTirillaCobroSincrono(eq("Ya Pagó S.A.S."), anyString(), anyString());
        verify(emailService, times(1)).enviarTirillaCobroSincrono("Pendiente Ltda.", "correo@pendiente.com", "$ 500.000");
    }
}
