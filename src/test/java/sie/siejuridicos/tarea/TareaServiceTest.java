package sie.siejuridicos.tarea;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sie.siejuridicos.caso.Caso;
import sie.siejuridicos.caso.CasoRepository;
import sie.siejuridicos.caso.Cliente;
import sie.siejuridicos.crm.ClienteCrmRepository;
import sie.siejuridicos.registro.RegistroSistemaService;
import sie.siejuridicos.tarea.dto.ActualizarTareaRequest;
import sie.siejuridicos.tarea.dto.CrearTareaRequest;
import sie.siejuridicos.tarea.dto.TareaResponse;
import sie.siejuridicos.usuario.RolUsuario;
import sie.siejuridicos.usuario.UsuarioInterno;
import sie.siejuridicos.usuario.UsuarioInternoRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Auditoría de permisos (pedido explícito del usuario): "esta habilidad la podra tener tanto
// admin como cualquiera de los abogados" para crear/asignar tareas, PERO en la página "Tareas"
// un ABOGADO solo debe ver lo que tiene asignado a sí mismo -- nunca lo de sus colegas -- mientras
// que ADMIN_GENERAL ve el total de la firma. Esa distinción de visibilidad es el único punto
// donde el rol sí importa, y es lo que estas pruebas verifican con más cuidado.
@ExtendWith(MockitoExtension.class)
class TareaServiceTest {

    @Mock
    private TareaRepository tareaRepository;
    @Mock
    private CasoRepository casoRepository;
    @Mock
    private ClienteCrmRepository clienteCrmRepository;
    @Mock
    private UsuarioInternoRepository usuarioInternoRepository;
    @Mock
    private RegistroSistemaService registroSistemaService;

    private TareaService crearServicio() {
        return new TareaService(tareaRepository, casoRepository, clienteCrmRepository, usuarioInternoRepository, registroSistemaService);
    }

    private static UsuarioInterno usuarioDe(Long id, String nombre, RolUsuario rol) {
        UsuarioInterno u = new UsuarioInterno();
        u.setId(id);
        u.setNombre(nombre);
        u.setRol(rol);
        return u;
    }

    private static Caso casoDe(Long id, String radicadoId) {
        Cliente cliente = new Cliente();
        cliente.setNombre("Cliente de Prueba");
        Caso caso = new Caso();
        caso.setId(id);
        caso.setRadicadoId(radicadoId);
        caso.setCliente(cliente);
        return caso;
    }

    @Test
    void unAbogadoSoloVeSusPropiasTareasPendientesEnLaPaginaDeTareas() {
        UsuarioInterno abogado = usuarioDe(2L, "Abogado Uno", RolUsuario.ABOGADO);
        Tarea tareaPropia = new Tarea();
        tareaPropia.setCaso(casoDe(1L, "RAD-1"));
        tareaPropia.setTitulo("Responder tutela");
        tareaPropia.setUsuarioAsignado(abogado);
        tareaPropia.setUsuarioCreador(abogado);

        when(tareaRepository.findByUsuarioAsignadoIdAndCompletadaFalseOrderByFechaVencimientoAsc(2L))
                .thenReturn(List.of(tareaPropia));

        List<TareaResponse> resultado = crearServicio().listarPendientes(abogado);

        assertEquals(1, resultado.size());
        assertEquals("Responder tutela", resultado.get(0).titulo());
        verify(tareaRepository, org.mockito.Mockito.never()).findByCompletadaFalseOrderByFechaVencimientoAsc();
    }

    @Test
    void unAdminGeneralVeTodasLasTareasPendientesDeLaFirma() {
        UsuarioInterno admin = usuarioDe(1L, "Admin General", RolUsuario.ADMIN_GENERAL);
        UsuarioInterno abogado = usuarioDe(2L, "Abogado Uno", RolUsuario.ABOGADO);
        Tarea tareaDeOtro = new Tarea();
        tareaDeOtro.setCaso(casoDe(1L, "RAD-1"));
        tareaDeOtro.setTitulo("Tarea de otro abogado");
        tareaDeOtro.setUsuarioAsignado(abogado);
        tareaDeOtro.setUsuarioCreador(admin);

        when(tareaRepository.findByCompletadaFalseOrderByFechaVencimientoAsc())
                .thenReturn(List.of(tareaDeOtro));

        List<TareaResponse> resultado = crearServicio().listarPendientes(admin);

        assertEquals(1, resultado.size());
        assertEquals("Tarea de otro abogado", resultado.get(0).titulo());
        verify(tareaRepository, org.mockito.Mockito.never())
                .findByUsuarioAsignadoIdAndCompletadaFalseOrderByFechaVencimientoAsc(any());
    }

    @Test
    void crearTareaLaAsignaAlUsuarioIndicadoNoAQuienLaCrea() {
        UsuarioInterno admin = usuarioDe(1L, "Admin General", RolUsuario.ADMIN_GENERAL);
        UsuarioInterno abogado = usuarioDe(2L, "Abogado Uno", RolUsuario.ABOGADO);
        Caso caso = casoDe(10L, "RAD-10");

        when(casoRepository.findById(10L)).thenReturn(Optional.of(caso));
        when(usuarioInternoRepository.findById(2L)).thenReturn(Optional.of(abogado));

        CrearTareaRequest request = new CrearTareaRequest("Revisar contrato", null, null, null, 2L);
        TareaResponse resultado = crearServicio().crear(10L, request, admin);

        assertEquals("Revisar contrato", resultado.titulo());
        assertEquals(2L, resultado.usuarioAsignadoId());
        assertEquals("Abogado Uno", resultado.usuarioAsignadoNombre());
        assertEquals(1L, resultado.usuarioCreadorId());
        assertEquals(PrioridadTarea.MEDIA, resultado.prioridad());
        assertFalse(resultado.completada());
        verify(tareaRepository).save(any(Tarea.class));
    }

    @Test
    void completarMarcaFechaCompletadaYReabrirLaLimpia() {
        UsuarioInterno abogado = usuarioDe(2L, "Abogado Uno", RolUsuario.ABOGADO);
        Tarea tarea = new Tarea();
        tarea.setCaso(casoDe(1L, "RAD-1"));
        tarea.setTitulo("Presentar escrito");
        tarea.setUsuarioAsignado(abogado);
        tarea.setUsuarioCreador(abogado);
        when(tareaRepository.findById(5L)).thenReturn(Optional.of(tarea));

        TareaResponse completada = crearServicio().completar(5L, true, abogado);
        assertTrue(completada.completada());
        assertNotNull(completada.fechaCompletada());

        TareaResponse reabierta = crearServicio().completar(5L, false, abogado);
        assertFalse(reabierta.completada());
        assertNull(reabierta.fechaCompletada());
    }

    @Test
    void actualizarPuedeReasignarLaTareaAOtroUsuario() {
        UsuarioInterno abogadoOriginal = usuarioDe(2L, "Abogado Uno", RolUsuario.ABOGADO);
        UsuarioInterno abogadoNuevo = usuarioDe(3L, "Abogado Dos", RolUsuario.ABOGADO);
        Tarea tarea = new Tarea();
        tarea.setCaso(casoDe(1L, "RAD-1"));
        tarea.setTitulo("Preparar audiencia");
        tarea.setUsuarioAsignado(abogadoOriginal);
        tarea.setUsuarioCreador(abogadoOriginal);
        when(tareaRepository.findById(7L)).thenReturn(Optional.of(tarea));
        when(usuarioInternoRepository.findById(3L)).thenReturn(Optional.of(abogadoNuevo));

        ActualizarTareaRequest request = new ActualizarTareaRequest("Preparar audiencia (urgente)", null, null, PrioridadTarea.ALTA, 3L);
        TareaResponse resultado = crearServicio().actualizar(7L, request, abogadoOriginal);

        assertEquals("Preparar audiencia (urgente)", resultado.titulo());
        assertEquals(PrioridadTarea.ALTA, resultado.prioridad());
        assertEquals(3L, resultado.usuarioAsignadoId());
        assertEquals("Abogado Dos", resultado.usuarioAsignadoNombre());
    }

    @Test
    void eliminarBorraLaTareaDelRepositorio() {
        UsuarioInterno admin = usuarioDe(1L, "Admin General", RolUsuario.ADMIN_GENERAL);
        Tarea tarea = new Tarea();
        tarea.setCaso(casoDe(1L, "RAD-1"));
        tarea.setTitulo("Tarea a borrar");
        tarea.setUsuarioAsignado(admin);
        tarea.setUsuarioCreador(admin);
        when(tareaRepository.findById(9L)).thenReturn(Optional.of(tarea));

        crearServicio().eliminar(9L, admin);

        verify(tareaRepository).delete(tarea);
    }

    // Bug real corregido en esta auditoría (pedido explícito del usuario, tras encontrar que el
    // 96% del directorio del CRM no tenía ningún caso judicial vinculado): una tarea ahora puede
    // crearse directamente para una persona del CRM, sin caso.
    @Test
    void crearParaClienteLigaLaTareaAlClienteCrmSinNecesitarUnCaso() {
        UsuarioInterno admin = usuarioDe(1L, "Admin General", RolUsuario.ADMIN_GENERAL);
        UsuarioInterno abogado = usuarioDe(2L, "Abogado Uno", RolUsuario.ABOGADO);
        sie.siejuridicos.crm.ClienteCrm cliente = new sie.siejuridicos.crm.ClienteCrm();
        cliente.setNombre("Prospecto Sin Caso Todavía");

        when(clienteCrmRepository.findById(55L)).thenReturn(Optional.of(cliente));
        when(usuarioInternoRepository.findById(2L)).thenReturn(Optional.of(abogado));

        CrearTareaRequest request = new CrearTareaRequest("Llamar para primera cita", null, null, null, 2L);
        TareaResponse resultado = crearServicio().crearParaCliente(55L, request, admin);

        assertEquals("Llamar para primera cita", resultado.titulo());
        assertNull(resultado.casoId());
        assertEquals("Prospecto Sin Caso Todavía", resultado.clienteNombre());
        assertEquals(2L, resultado.usuarioAsignadoId());
        verify(tareaRepository).save(any(Tarea.class));
    }
}
