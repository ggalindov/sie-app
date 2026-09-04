package sie.siejuridicos.articulo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sie.siejuridicos.articulo.dto.ActualizarArticuloRequest;
import sie.siejuridicos.boletin.BoletinEnviadoRepository;
import sie.siejuridicos.categoria.Categoria;
import sie.siejuridicos.categoria.CategoriaRepository;
import sie.siejuridicos.correo.EmailService;
import sie.siejuridicos.marketing.SuscriptorMarketingRepository;
import sie.siejuridicos.registro.RegistroSistemaService;
import sie.siejuridicos.usuario.UsuarioInterno;
import sie.siejuridicos.usuario.UsuarioInternoRepository;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Pedido explícito del usuario: avisar por correo a quien sube contenido a redes sociales
// cada vez que se publica un blog/noticia de verdad. Estas pruebas cubren que
// ArticuloService.actualizar() dispara ese aviso exactamente en la transición real a
// PUBLICADO -- ni antes, ni en ediciones posteriores de algo que ya estaba publicado -- y
// que NO depende de si hay suscriptores del boletín (son dos avisos independientes).
@ExtendWith(MockitoExtension.class)
class ArticuloServiceNotificacionesTest {

    @Mock
    private ArticuloRepository articuloRepository;
    @Mock
    private CategoriaRepository categoriaRepository;
    @Mock
    private UsuarioInternoRepository usuarioInternoRepository;
    @Mock
    private SuscriptorMarketingRepository suscriptorMarketingRepository;
    @Mock
    private BoletinEnviadoRepository boletinEnviadoRepository;
    @Mock
    private EmailService emailService;
    @Mock
    private RegistroSistemaService registroSistemaService;

    private ArticuloService crearServicio() {
        return new ArticuloService(articuloRepository, categoriaRepository, usuarioInternoRepository,
                suscriptorMarketingRepository, boletinEnviadoRepository, emailService, registroSistemaService);
    }

    private static Categoria categoria(long id) {
        Categoria categoria = new Categoria();
        categoria.setId(id);
        return categoria;
    }

    private static UsuarioInterno autor() {
        UsuarioInterno autor = new UsuarioInterno();
        autor.setId(1L);
        autor.setNombre("Autor de prueba");
        return autor;
    }

    private static Articulo articuloBorrador(long id) {
        Articulo articulo = new Articulo();
        articulo.setId(id);
        articulo.setTitulo("Título de prueba");
        articulo.setSlug("titulo-de-prueba");
        articulo.setContenido("Contenido");
        articulo.setCategoria(categoria(1L));
        articulo.setAutor(autor());
        articulo.setEstado(EstadoArticulo.BORRADOR);
        return articulo;
    }

    private static ActualizarArticuloRequest solicitudPublicar() {
        return new ActualizarArticuloRequest(
                "Título de prueba", "Contenido", null, null,
                TipoContenido.BLOG, 1L, null, EstadoArticulo.PUBLICADO);
    }

    @Test
    void publicarUnBorradorAvisaRedesSocialesAunqueNoHayaSuscriptores() {
        Articulo borrador = articuloBorrador(10L);
        Articulo publicado = articuloBorrador(10L);
        publicado.setEstado(EstadoArticulo.PUBLICADO);

        when(articuloRepository.findById(10L)).thenReturn(Optional.of(borrador));
        when(articuloRepository.saveAndFlush(borrador)).thenReturn(borrador);
        when(articuloRepository.publicarArticulo(10L)).thenReturn(publicado);
        when(suscriptorMarketingRepository.findByActivoTrueOrderByFechaSuscripcionDesc()).thenReturn(List.of());

        crearServicio().actualizar(10L, solicitudPublicar());

        ArgumentCaptor<Articulo> captor = ArgumentCaptor.forClass(Articulo.class);
        verify(emailService).enviarAvisoRedesSociales(captor.capture());
        assertSame(publicado, captor.getValue());

        // Sin suscriptores, el boletín no se envía -- pero eso no debe impedir el aviso de
        // redes de arriba, son dos cosas independientes.
        verify(emailService, never()).enviarNotificacionPublicacion(any(), any());
    }

    @Test
    void editarUnArticuloYaPublicadoNuncaReenviaElAvisoDeRedes() {
        Articulo yaPublicado = articuloBorrador(20L);
        yaPublicado.setEstado(EstadoArticulo.PUBLICADO);

        when(articuloRepository.findById(20L)).thenReturn(Optional.of(yaPublicado));
        when(articuloRepository.saveAndFlush(yaPublicado)).thenReturn(yaPublicado);
        when(articuloRepository.publicarArticulo(20L)).thenReturn(yaPublicado);

        crearServicio().actualizar(20L, solicitudPublicar());

        verify(emailService, never()).enviarAvisoRedesSociales(any());
    }
}
