package sie.siejuridicos.cobro;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sie.siejuridicos.cobro.dto.ResumenEnvioRecordatoriosCobros;
import sie.siejuridicos.common.cifrado.CifradoService;
import sie.siejuridicos.common.limite.LimiteEnvioMasivoService;
import sie.siejuridicos.correo.EmailService;
import sie.siejuridicos.registro.RegistroSistemaService;
import sie.siejuridicos.whatsapp.WhatsAppService;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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
    @Mock
    private LimiteEnvioMasivoService limiteEnvioMasivoService;

    private CobroService crearServicio() {
        // lenient(): el cupo diario compartido (ver LimiteEnvioMasivoService) es una barrera
        // nueva delante del envío real, ortogonal a lo que prueba esta clase (cruce de datos
        // entre clientes) -- se deja siempre "hay cupo" aquí para que esas pruebas no tengan
        // que conocer ese detalle. El límite en sí se prueba aparte.
        lenient().when(limiteEnvioMasivoService.intentarReservarCupo()).thenReturn(true);
        return new CobroService(clienteCobroRepository, hojaCobrosService, emailService, whatsAppService,
                cifradoService, registroSistemaService, limiteEnvioMasivoService);
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

    // Mismo mecanismo que CasoServiceTest.alAgotarseElCupoDiarioLosRestantesQuedanPendientesSinIntentarNingunEnvio
    // -- el cupo diario de envíos masivos es COMPARTIDO entre casos y cobros (ver
    // LimiteEnvioMasivoService), así que esta prueba confirma que también aquí, al agotarse,
    // los clientes restantes no reciben ningún intento de envío y quedan sin marcar
    // (fechaUltimoRecordatorio intacta) para que la corrida de mañana los recoja.
    @Test
    void alAgotarseElCupoDiarioLosClientesRestantesQuedanPendientesSinIntentarNingunEnvio() {
        ClienteCobro a = clienteDe("1", "Empresa Alfa S.A.S.", "pagos@alfa.com", "3001112222", "$ 1.000.000");
        ClienteCobro b = clienteDe("2", "Empresa Beta Ltda.", "pagos@beta.com", "3003334444", "$ 2.500.000");
        ClienteCobro c = clienteDe("3", "Empresa Gamma S.A.", "pagos@gamma.com", "3005556666", "$ 750.000");
        when(clienteCobroRepository.findByActivoTrueOrderByNombreAsc()).thenReturn(List.of(a, b, c));
        when(whatsAppService.isConfigurado()).thenReturn(true);
        when(emailService.enviarTirillaCobroSincrono(anyString(), anyString(), anyString())).thenReturn(true);
        when(whatsAppService.enviarRecordatorioCobroSincrono(anyString(), anyString(), anyString())).thenReturn(true);
        CobroService servicio = crearServicio();
        // Cupo disponible solo para el primer cliente del lote -- DESPUÉS de crearServicio(),
        // que ya deja un stub por defecto ("siempre hay cupo") sobre el mismo mock: el último
        // when() registrado es el que manda.
        when(limiteEnvioMasivoService.intentarReservarCupo()).thenReturn(true, false, false);

        ResumenEnvioRecordatoriosCobros resumen = servicio.enviarRecordatorios();

        verify(emailService, times(1)).enviarTirillaCobroSincrono("Empresa Alfa S.A.S.", "pagos@alfa.com", "$ 1.000.000");
        verify(whatsAppService, times(1)).enviarRecordatorioCobroSincrono("Empresa Alfa S.A.S.", "3001112222", "$ 1.000.000");
        verify(emailService, never()).enviarTirillaCobroSincrono(eq("Empresa Beta Ltda."), anyString(), anyString());
        verify(emailService, never()).enviarTirillaCobroSincrono(eq("Empresa Gamma S.A."), anyString(), anyString());
        verify(whatsAppService, never()).enviarRecordatorioCobroSincrono(eq("Empresa Beta Ltda."), anyString(), anyString());
        verify(whatsAppService, never()).enviarRecordatorioCobroSincrono(eq("Empresa Gamma S.A."), anyString(), anyString());

        assertEquals(1, resumen.correosEnviados());
        assertEquals(1, resumen.whatsappEnviados());
        assertEquals(2, resumen.pendientesPorLimiteDiario());

        verify(clienteCobroRepository, times(1)).save(a);
        verify(clienteCobroRepository, never()).save(b);
        verify(clienteCobroRepository, never()).save(c);
    }

    @Test
    void unFalloInesperadoEnUnClienteNoInterrumpeElLoteDeLosDemas() {
        ClienteCobro a = clienteDe("1", "Cliente Problemático", "problema@alfa.com", null, "$ 1.000.000");
        ClienteCobro b = clienteDe("2", "Cliente Exitoso", "exito@beta.com", null, "$ 2.000.000");
        when(clienteCobroRepository.findByActivoTrueOrderByNombreAsc()).thenReturn(List.of(a, b));
        when(emailService.enviarTirillaCobroSincrono(eq("Cliente Problemático"), anyString(), anyString()))
                .thenThrow(new RuntimeException("Simulación de fallo inesperado de red"));
        when(emailService.enviarTirillaCobroSincrono(eq("Cliente Exitoso"), anyString(), anyString())).thenReturn(true);

        CobroService servicio = crearServicio();
        ResumenEnvioRecordatoriosCobros resumen = servicio.enviarRecordatorios();

        // Cliente Problemático falló pero no abortó el bucle
        verify(emailService, times(1)).enviarTirillaCobroSincrono("Cliente Problemático", "problema@alfa.com", "$ 1.000.000");
        // Cliente Exitoso sí se procesó y envió normalmente
        verify(emailService, times(1)).enviarTirillaCobroSincrono("Cliente Exitoso", "exito@beta.com", "$ 2.000.000");

        assertEquals(1, resumen.correosEnviados());
        assertEquals(1, resumen.correosFallidos());
        verify(clienteCobroRepository, times(1)).save(b);
    }

    @Test
    void siCorreoYaFueEnviadoEsteMesSoloSeEnviaWhatsapp() {
        ClienteCobro a = clienteDe("1", "Empresa Alfa S.A.S.", "pagos@alfa.com", "3001112222", "$ 1.000.000");
        // Correo ya enviado previamente este mes, pero WhatsApp pendiente
        LocalDateTime haceDosDias = LocalDateTime.now().minusDays(2);
        a.setFechaUltimoRecordatorioCorreo(haceDosDias);
        a.setFechaUltimoRecordatorio(haceDosDias);

        when(clienteCobroRepository.findByActivoTrueOrderByNombreAsc()).thenReturn(List.of(a));
        when(whatsAppService.isConfigurado()).thenReturn(true);
        when(whatsAppService.enviarRecordatorioCobroSincrono(anyString(), anyString(), anyString())).thenReturn(true);

        CobroService servicio = crearServicio();
        ResumenEnvioRecordatoriosCobros resumen = servicio.enviarRecordatorios();

        // No debe reenviar el correo
        verify(emailService, never()).enviarTirillaCobroSincrono(anyString(), anyString(), anyString());
        // Sí debe enviar el WhatsApp pendiente
        verify(whatsAppService, times(1)).enviarRecordatorioCobroSincrono("Empresa Alfa S.A.S.", "3001112222", "$ 1.000.000");

        assertEquals(0, resumen.correosEnviados());
        assertEquals(1, resumen.whatsappEnviados());
        assertNotNull(a.getFechaUltimoRecordatorioWhatsapp());
        assertEquals(haceDosDias, a.getFechaUltimoRecordatorioCorreo());
        verify(clienteCobroRepository, times(1)).save(a);
    }

    @Test
    void whatsappSeEnviaSoloUnaVezPorChatSiHayFilasConMismoTelefono() {
        ClienteCobro a = clienteDe("1", "Empresa Alfa Fila 1", null, "3001112222", "$ 1.000.000");
        ClienteCobro b = clienteDe("2", "Empresa Alfa Fila 2", null, "+57 300 111 2222", "$ 2.000.000");

        when(clienteCobroRepository.findByActivoTrueOrderByNombreAsc()).thenReturn(List.of(a, b));
        when(whatsAppService.isConfigurado()).thenReturn(true);
        when(whatsAppService.enviarRecordatorioCobroSincrono(anyString(), anyString(), anyString())).thenReturn(true);

        CobroService servicio = crearServicio();
        ResumenEnvioRecordatoriosCobros resumen = servicio.enviarRecordatorios();

        // Se envía EXACTAMENTE un WhatsApp a ese número
        verify(whatsAppService, times(1)).enviarRecordatorioCobroSincrono(eq("Empresa Alfa Fila 1"), anyString(), eq("$ 1.000.000"));
        verify(whatsAppService, never()).enviarRecordatorioCobroSincrono(eq("Empresa Alfa Fila 2"), anyString(), anyString());

        assertEquals(1, resumen.whatsappEnviados());
        assertNotNull(a.getFechaUltimoRecordatorioWhatsapp());
        assertNotNull(b.getFechaUltimoRecordatorioWhatsapp());
        verify(clienteCobroRepository, times(1)).save(a);
        verify(clienteCobroRepository, times(1)).save(b);
    }

    @Test
    void siAmbosCanalesYaFueronEnviadosEsteMesSeSaltaSinConsumirCupo() {
        ClienteCobro a = clienteDe("1", "Empresa Alfa S.A.S.", "pagos@alfa.com", "3001112222", "$ 1.000.000");
        LocalDateTime haceDosDias = LocalDateTime.now().minusDays(2);
        a.setFechaUltimoRecordatorioCorreo(haceDosDias);
        a.setFechaUltimoRecordatorioWhatsapp(haceDosDias);
        a.setFechaUltimoRecordatorio(haceDosDias);

        when(clienteCobroRepository.findByActivoTrueOrderByNombreAsc()).thenReturn(List.of(a));
        when(whatsAppService.isConfigurado()).thenReturn(true);

        CobroService servicio = crearServicio();
        ResumenEnvioRecordatoriosCobros resumen = servicio.enviarRecordatorios();

        verify(emailService, never()).enviarTirillaCobroSincrono(anyString(), anyString(), anyString());
        verify(whatsAppService, never()).enviarRecordatorioCobroSincrono(anyString(), anyString(), anyString());
        verify(limiteEnvioMasivoService, never()).intentarReservarCupo();
        verify(clienteCobroRepository, never()).save(a);

        assertEquals(0, resumen.correosEnviados());
        assertEquals(0, resumen.whatsappEnviados());
    }

    // Auditoría de bug real (pedido explícito del usuario): "no se está limpiando esos check
    // antes de que se acabe el mes para que no se bugee la información". Un cliente que pagó
    // (o dijo que no) en un mes anterior debe quedar en blanco al reiniciar -- en nuestra base
    // de datos Y en el Google Sheets, para que el check que el equipo olvidó desmarcar en la
    // hoja no vuelva a colarse como "ya pagó" en la próxima sincronización.
    @Test
    void reiniciarEstadoMensualLimpiaClientesConRespuestaDeUnMesAnterior() {
        ClienteCobro pagoMesPasado = clienteDe("1", "Pagó El Mes Pasado S.A.S.", "correo@pasado.com", null, "$ 500.000");
        pagoMesPasado.setPagoEsteMes(true);
        pagoMesPasado.setRespondioMensaje("Sí");
        pagoMesPasado.setMesRespuesta(YearMonth.now().minusMonths(1).toString());

        when(clienteCobroRepository.findByActivoTrueOrderByNombreAsc()).thenReturn(List.of(pagoMesPasado));

        int reiniciados = crearServicio().reiniciarEstadoMensual();

        assertEquals(1, reiniciados);
        assertNull(pagoMesPasado.getPagoEsteMes());
        assertNull(pagoMesPasado.getRespondioMensaje());
        assertNull(pagoMesPasado.getMesRespuesta());
        verify(clienteCobroRepository, times(1)).save(pagoMesPasado);
        verify(hojaCobrosService, times(1)).limpiarPagoMensual(TipoClienteCobro.EMPRESA, "1");
        verify(registroSistemaService, times(1))
                .registrar(eq(sie.siejuridicos.registro.TipoRegistroSistema.REINICIO_MENSUAL_COBROS), anyString(), eq(true));
    }

    // No debe tocar -- ni en la base de datos ni en la hoja -- a un cliente cuya respuesta ya es
    // de este mismo mes, ni a uno que nunca ha respondido (mesRespuesta null).
    @Test
    void reiniciarEstadoMensualNoTocaClientesDelMesActualNiSinRespuesta() {
        ClienteCobro pagoEsteMesCliente = clienteDe("1", "Pagó Este Mes S.A.S.", "correo@actual.com", null, "$ 500.000");
        pagoEsteMesCliente.setPagoEsteMes(true);
        pagoEsteMesCliente.setRespondioMensaje("Sí");
        pagoEsteMesCliente.setMesRespuesta(YearMonth.now().toString());

        ClienteCobro sinResponder = clienteDe("2", "Nunca Respondió Ltda.", "correo@sinresponder.com", null, "$ 500.000");

        when(clienteCobroRepository.findByActivoTrueOrderByNombreAsc())
                .thenReturn(List.of(pagoEsteMesCliente, sinResponder));

        int reiniciados = crearServicio().reiniciarEstadoMensual();

        assertEquals(0, reiniciados);
        assertEquals(Boolean.TRUE, pagoEsteMesCliente.getPagoEsteMes());
        verify(clienteCobroRepository, never()).save(any(ClienteCobro.class));
        verify(hojaCobrosService, never()).limpiarPagoMensual(any(), anyString());
        verify(registroSistemaService, never())
                .registrar(eq(sie.siejuridicos.registro.TipoRegistroSistema.REINICIO_MENSUAL_COBROS), anyString(), eq(true));
    }

    // El escenario exacto que reportó el usuario como bugeado: un cliente marcado pagoEsteMes=true
    // el mes pasado se saltaba el recordatorio de este mes indefinidamente porque nada limpiaba
    // ese check. Confirma que enviarRecordatorios() se autocorrige y SÍ le envía el recordatorio.
    @Test
    void unClienteConPagoDeUnMesAnteriorSiRecibeElRecordatorioDeEsteMes() {
        ClienteCobro pagoMesPasado = clienteDe("1", "Pagó El Mes Pasado S.A.S.", "correo@pasado.com", null, "$ 500.000");
        pagoMesPasado.setPagoEsteMes(true);
        pagoMesPasado.setRespondioMensaje("Sí");
        pagoMesPasado.setMesRespuesta(YearMonth.now().minusMonths(1).toString());

        when(clienteCobroRepository.findByActivoTrueOrderByNombreAsc()).thenReturn(List.of(pagoMesPasado));
        when(emailService.enviarTirillaCobroSincrono(anyString(), anyString(), anyString())).thenReturn(true);

        crearServicio().enviarRecordatorios();

        verify(emailService, times(1))
                .enviarTirillaCobroSincrono("Pagó El Mes Pasado S.A.S.", "correo@pasado.com", "$ 500.000");
    }
}
