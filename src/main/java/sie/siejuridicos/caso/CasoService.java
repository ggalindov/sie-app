package sie.siejuridicos.caso;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sie.siejuridicos.caso.dto.CasoAdminResponse;
import sie.siejuridicos.caso.dto.CasoConsultaResponse;
import sie.siejuridicos.caso.dto.CrearCasoRequest;
import sie.siejuridicos.categoria.Categoria;
import sie.siejuridicos.categoria.CategoriaRepository;
import sie.siejuridicos.common.exception.RecursoNoEncontradoException;
import sie.siejuridicos.correo.EmailService;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;

@Service
public class CasoService {

    // Sin 0/O, 1/I/L: alfabeto pensado para que el cliente transcriba el código a mano
    // (lo recibe por correo) sin confundir caracteres parecidos.
    private static final String ALFABETO_CODIGO = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final int LONGITUD_SUFIJO = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CasoRepository casoRepository;
    private final ClienteRepository clienteRepository;
    private final CategoriaRepository categoriaRepository;
    private final EmailService emailService;

    public CasoService(CasoRepository casoRepository,
                        ClienteRepository clienteRepository,
                        CategoriaRepository categoriaRepository,
                        EmailService emailService) {
        this.casoRepository = casoRepository;
        this.clienteRepository = clienteRepository;
        this.categoriaRepository = categoriaRepository;
        this.emailService = emailService;
    }

    @Transactional
    public CasoAdminResponse crear(CrearCasoRequest request) {
        Categoria categoria = categoriaRepository.findById(request.idCategoria())
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la categoría con id " + request.idCategoria()));

        Cliente cliente = clienteRepository.findByCorreo(request.correoCliente())
                .map(existente -> {
                    existente.setNombre(request.nombreCliente());
                    existente.setTelefono(request.telefonoCliente());
                    return existente;
                })
                .orElseGet(() -> {
                    Cliente nuevo = new Cliente();
                    nuevo.setNombre(request.nombreCliente());
                    nuevo.setCorreo(request.correoCliente());
                    nuevo.setTelefono(request.telefonoCliente());
                    return clienteRepository.save(nuevo);
                });

        Caso caso = new Caso();
        caso.setCliente(cliente);
        caso.setCategoria(categoria);
        caso.setCodigoUnico(generarCodigoUnico());
        caso.setEtapa(EtapaCaso.RADICADO);
        caso.setNotasInternas(request.notasInternas());
        caso.setFechaActualizacion(LocalDateTime.now());

        Caso guardado = casoRepository.save(caso);

        emailService.enviarCodigoCaso(cliente.getNombre(), cliente.getCorreo(), guardado.getCodigoUnico(), categoria.getNombre());

        return CasoAdminResponse.desde(guardado);
    }

    @Transactional(readOnly = true)
    public List<CasoAdminResponse> listarTodos() {
        return casoRepository.listarTodosConDetalle().stream()
                .map(CasoAdminResponse::desde)
                .toList();
    }

    @Transactional
    public CasoAdminResponse actualizarEtapa(Long id, EtapaCaso nuevaEtapa) {
        Caso caso = casoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el caso con id " + id));
        caso.setEtapa(nuevaEtapa);
        caso.setFechaActualizacion(LocalDateTime.now());
        return CasoAdminResponse.desde(caso);
    }

    @Transactional(readOnly = true)
    public CasoConsultaResponse consultar(String codigo) {
        Caso caso = casoRepository.findByCodigoUnico(codigo.strip().toUpperCase())
                .orElseThrow(() -> new RecursoNoEncontradoException("No encontramos ningún caso con ese código"));
        return CasoConsultaResponse.desde(caso);
    }

    private String generarCodigoUnico() {
        String codigo;
        do {
            StringBuilder sb = new StringBuilder("SIE-").append(Year.now()).append('-');
            for (int i = 0; i < LONGITUD_SUFIJO; i++) {
                sb.append(ALFABETO_CODIGO.charAt(RANDOM.nextInt(ALFABETO_CODIGO.length())));
            }
            codigo = sb.toString();
        } while (casoRepository.existsByCodigoUnico(codigo));
        return codigo;
    }
}
