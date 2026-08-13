package sie.siejuridicos.articulo;

import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sie.siejuridicos.articulo.dto.ActualizarArticuloRequest;
import sie.siejuridicos.articulo.dto.ArticuloDetalleResponse;
import sie.siejuridicos.articulo.dto.ArticuloResumenResponse;
import sie.siejuridicos.articulo.dto.CrearArticuloRequest;
import sie.siejuridicos.categoria.Categoria;
import sie.siejuridicos.categoria.CategoriaRepository;
import sie.siejuridicos.common.exception.ErroresBaseDatos;
import sie.siejuridicos.common.exception.RecursoNoEncontradoException;
import sie.siejuridicos.usuario.UsuarioInternoRepository;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class ArticuloService {

    private static final Pattern CARACTERES_NO_VALIDOS = Pattern.compile("[^a-z0-9\\s-]");
    private static final Pattern ESPACIOS_O_GUIONES = Pattern.compile("[\\s-]+");

    private final ArticuloRepository articuloRepository;
    private final CategoriaRepository categoriaRepository;
    private final UsuarioInternoRepository usuarioInternoRepository;

    public ArticuloService(ArticuloRepository articuloRepository,
                            CategoriaRepository categoriaRepository,
                            UsuarioInternoRepository usuarioInternoRepository) {
        this.articuloRepository = articuloRepository;
        this.categoriaRepository = categoriaRepository;
        this.usuarioInternoRepository = usuarioInternoRepository;
    }

    @Transactional(readOnly = true)
    public List<ArticuloResumenResponse> listarPublicados(Long idCategoria, String busqueda) {
        String terminoBusqueda = (busqueda == null || busqueda.isBlank()) ? null : busqueda.trim();
        return articuloRepository.buscarPublicados(idCategoria, terminoBusqueda).stream()
                .map(ArticuloResumenResponse::desde)
                .toList();
    }

    @Transactional(readOnly = true)
    public ArticuloDetalleResponse obtenerPublicadoPorSlug(String slug) {
        Articulo articulo = articuloRepository.findBySlugAndEstado(slug, EstadoArticulo.PUBLICADO)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe un artículo publicado con slug " + slug));
        return ArticuloDetalleResponse.desde(articulo);
    }

    // el panel administrativo necesita ver TODO (borradores incluidos), a diferencia
    // del listado público que solo muestra PUBLICADO (buscarPublicados)
    @Transactional(readOnly = true)
    public List<ArticuloDetalleResponse> listarTodos() {
        return articuloRepository.findAll(Sort.by(Sort.Direction.DESC, "fechaCreacion")).stream()
                .map(ArticuloDetalleResponse::desde)
                .toList();
    }

    @Transactional(readOnly = true)
    public ArticuloDetalleResponse obtenerPorId(Long id) {
        Articulo articulo = articuloRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el artículo con id " + id));
        return ArticuloDetalleResponse.desde(articulo);
    }

    @Transactional
    public ArticuloDetalleResponse crear(CrearArticuloRequest request, Long autorId) {
        Categoria categoria = categoriaRepository.findById(request.idCategoria())
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la categoría con id " + request.idCategoria()));

        Articulo articulo = new Articulo();
        articulo.setTitulo(request.titulo());
        articulo.setSlug(generarSlugUnico(request.titulo()));
        articulo.setContenido(request.contenido());
        articulo.setResumen(request.resumen());
        articulo.setCategoria(categoria);
        articulo.setAutor(usuarioInternoRepository.getReferenceById(autorId));
        articulo.setTiempoLecturaMin(request.tiempoLecturaMin());

        return ArticuloDetalleResponse.desde(articuloRepository.save(articulo));
    }

    // @Transactional (sin readOnly) es necesario aquí: cuando estado=PUBLICADO se invoca
    // fn_publicar_articulo, que hace un UPDATE internamente.
    @Transactional
    public ArticuloDetalleResponse actualizar(Long id, ActualizarArticuloRequest request) {
        Articulo articulo = articuloRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el artículo con id " + id));

        if (!articulo.getCategoria().getId().equals(request.idCategoria())) {
            Categoria categoria = categoriaRepository.findById(request.idCategoria())
                    .orElseThrow(() -> new RecursoNoEncontradoException("No existe la categoría con id " + request.idCategoria()));
            articulo.setCategoria(categoria);
        }
        articulo.setTitulo(request.titulo());
        articulo.setContenido(request.contenido());
        articulo.setResumen(request.resumen());
        articulo.setTiempoLecturaMin(request.tiempoLecturaMin());

        if (request.estado() == EstadoArticulo.PUBLICADO) {
            // fn_publicar_articulo valida integridad (título/contenido/categoría) contra la fila
            // ya persistida, por eso se hace flush antes de invocarla.
            articuloRepository.saveAndFlush(articulo);
            try {
                return ArticuloDetalleResponse.desde(articuloRepository.publicarArticulo(id));
            } catch (DataAccessException ex) {
                throw ErroresBaseDatos.traducir(ex);
            }
        }

        articulo.setEstado(EstadoArticulo.BORRADOR);
        return ArticuloDetalleResponse.desde(articuloRepository.save(articulo));
    }

    @Transactional
    public void eliminar(Long id) {
        if (!articuloRepository.existsById(id)) {
            throw new RecursoNoEncontradoException("No existe el artículo con id " + id);
        }
        articuloRepository.deleteById(id);
    }

    // RF-12: slug amigable y único generado a partir del título
    private String generarSlugUnico(String titulo) {
        String base = normalizarASlug(titulo);
        String slug = base;
        int sufijo = 2;
        while (articuloRepository.existsBySlug(slug)) {
            slug = base + "-" + sufijo++;
        }
        return slug;
    }

    private String normalizarASlug(String texto) {
        String sinAcentos = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String sinCaracteresInvalidos = CARACTERES_NO_VALIDOS.matcher(sinAcentos.toLowerCase(Locale.ROOT)).replaceAll("");
        return ESPACIOS_O_GUIONES.matcher(sinCaracteresInvalidos.trim()).replaceAll("-");
    }
}
