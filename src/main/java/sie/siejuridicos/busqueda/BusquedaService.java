package sie.siejuridicos.busqueda;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sie.siejuridicos.caso.Caso;
import sie.siejuridicos.caso.CasoRepository;
import sie.siejuridicos.caso.Cliente;
import sie.siejuridicos.crm.ClienteCrm;
import sie.siejuridicos.crm.ClienteCrmRepository;
import sie.siejuridicos.documento.DocumentoFirma;
import sie.siejuridicos.documento.DocumentoFirmaRepository;
import sie.siejuridicos.documento.DocumentoFirmante;
import sie.siejuridicos.documento.DocumentoFirmanteRepository;
import sie.siejuridicos.solicitud.Solicitud;
import sie.siejuridicos.solicitud.SolicitudRepository;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

// Buscador total del panel admin (ADMIN_GENERAL y ABOGADO): busca por nombre o por radicado
// a la vez en Casos, Clientes CRM, Solicitudes y Documentos para firma. Usuarios internos,
// Testimonios, Marketing y Cobros quedan FUERA a propósito (son módulos exclusivos de
// ADMIN_GENERAL, ver SecurityConfig -- este endpoint cae bajo el catch-all
// hasAnyRole('ADMIN_GENERAL','ABOGADO'), así que incluir esos módulos filtraría resultados
// que un ABOGADO no debería ver).
//
// Caso/ClienteCrm.nombre están CIFRADOS en la base de datos (CampoCifradoConverter) -- no
// se puede empujar un LIKE al SQL sobre esas columnas, el converter descifra de forma
// transparente al leer la entidad, así que el filtro por nombre en esos dos módulos
// necesariamente carga en memoria y compara con el valor ya descifrado (mismo patrón ya
// usado en CrmService.listarClientes para el directorio de clientes). Radicado/número de
// caso, en cambio, son planos -- eso sí va directo a SQL.
@Service
public class BusquedaService {

    private static final int LARGO_MINIMO = 2;
    private static final int LIMITE_POR_CATEGORIA = 6;

    private final CasoRepository casoRepository;
    private final ClienteCrmRepository clienteCrmRepository;
    private final SolicitudRepository solicitudRepository;
    private final DocumentoFirmaRepository documentoFirmaRepository;
    private final DocumentoFirmanteRepository documentoFirmanteRepository;

    public BusquedaService(CasoRepository casoRepository,
                            ClienteCrmRepository clienteCrmRepository,
                            SolicitudRepository solicitudRepository,
                            DocumentoFirmaRepository documentoFirmaRepository,
                            DocumentoFirmanteRepository documentoFirmanteRepository) {
        this.casoRepository = casoRepository;
        this.clienteCrmRepository = clienteCrmRepository;
        this.solicitudRepository = solicitudRepository;
        this.documentoFirmaRepository = documentoFirmaRepository;
        this.documentoFirmanteRepository = documentoFirmanteRepository;
    }

    @Transactional(readOnly = true)
    public List<ResultadoBusqueda> buscar(String q) {
        if (q == null) {
            return List.of();
        }
        String texto = q.strip();
        if (texto.length() < LARGO_MINIMO) {
            return List.of();
        }
        String textoLower = texto.toLowerCase(Locale.ROOT);

        List<ResultadoBusqueda> resultados = new ArrayList<>();
        resultados.addAll(buscarCasos(texto, textoLower));
        resultados.addAll(buscarClientes(textoLower));
        resultados.addAll(buscarSolicitudes(texto));
        resultados.addAll(buscarDocumentos(texto));
        return resultados;
    }

    private List<ResultadoBusqueda> buscarCasos(String texto, String textoLower) {
        // 1. Radicado/número de caso primero (plano, SQL directo) -- es la coincidencia más
        // inequívoca, la que el usuario casi siempre quiere cuando teclea algo parecido a un
        // radicado.
        List<Caso> porRadicado = casoRepository
                .findByRadicadoIdContainingIgnoreCaseOrNumeroCasoContainingIgnoreCase(texto, texto);
        if (!porRadicado.isEmpty()) {
            return porRadicado.stream()
                    .limit(LIMITE_POR_CATEGORIA)
                    .map(c -> new ResultadoBusqueda("CASO", String.valueOf(c.getId()), identificadorCaso(c),
                            nombreClienteDeCaso(c), rutaConQuery("/admin/casos", texto)))
                    .toList();
        }

        // 2. Si nada coincidió por radicado/número, probar por nombre del cliente (cifrado,
        // necesariamente en memoria -- ver nota de la clase).
        return casoRepository.listarTodosConDetalle().stream()
                .filter(c -> coincideNombreCaso(c, textoLower))
                .limit(LIMITE_POR_CATEGORIA)
                .map(c -> new ResultadoBusqueda("CASO", String.valueOf(c.getId()), identificadorCaso(c),
                        nombreClienteDeCaso(c), rutaConQuery("/admin/casos", texto)))
                .toList();
    }

    private boolean coincideNombreCaso(Caso caso, String textoLower) {
        if (caso.getNombreEnHoja() != null && caso.getNombreEnHoja().toLowerCase(Locale.ROOT).contains(textoLower)) {
            return true;
        }
        Cliente cliente = caso.getCliente();
        return cliente != null && cliente.getNombre() != null
                && cliente.getNombre().toLowerCase(Locale.ROOT).contains(textoLower);
    }

    private String identificadorCaso(Caso caso) {
        if (caso.getRadicadoId() != null && !caso.getRadicadoId().isBlank()) {
            return caso.getRadicadoId();
        }
        return caso.getNumeroCaso() != null ? caso.getNumeroCaso() : "Sin radicado";
    }

    private String nombreClienteDeCaso(Caso caso) {
        if (caso.getNombreEnHoja() != null && !caso.getNombreEnHoja().isBlank()) {
            return caso.getNombreEnHoja();
        }
        Cliente cliente = caso.getCliente();
        return cliente != null && cliente.getNombre() != null ? cliente.getNombre() : "";
    }

    private List<ResultadoBusqueda> buscarClientes(String textoLower) {
        return clienteCrmRepository.findAllByOrderByFechaCreacionDesc().stream()
                .filter(c -> c.getNombre() != null && c.getNombre().toLowerCase(Locale.ROOT).contains(textoLower))
                .limit(LIMITE_POR_CATEGORIA)
                .map(this::aResultadoCliente)
                .toList();
    }

    private ResultadoBusqueda aResultadoCliente(ClienteCrm cliente) {
        String subtitulo = cliente.getTipo() != null ? etiquetaTipoCliente(cliente.getTipo().name()) : "";
        return new ResultadoBusqueda("CLIENTE", String.valueOf(cliente.getId()), cliente.getNombre(), subtitulo,
                rutaConQuery("/admin/crm", cliente.getNombre()));
    }

    private String etiquetaTipoCliente(String tipo) {
        return "EMPRESA".equals(tipo) ? "Empresa" : "Persona natural";
    }

    private List<ResultadoBusqueda> buscarSolicitudes(String texto) {
        return solicitudRepository.findByNombreContainingIgnoreCase(texto).stream()
                .limit(LIMITE_POR_CATEGORIA)
                .map(s -> new ResultadoBusqueda("SOLICITUD", String.valueOf(s.getId()), s.getNombre(),
                        subtituloSolicitud(s), rutaConQuery("/admin/solicitudes", texto)))
                .toList();
    }

    private String subtituloSolicitud(Solicitud s) {
        return s.getCorreo() != null ? s.getCorreo() : "";
    }

    // Combina DocumentoFirma.nombre y DocumentoFirmante.nombreCapturado: un documento puede
    // aparecer porque su propio nombre coincide, o porque coincide el nombre de alguno de
    // sus firmantes -- en cualquier caso el resultado es el DOCUMENTO (no el firmante por
    // separado, que no tiene su propia página).
    private List<ResultadoBusqueda> buscarDocumentos(String texto) {
        LinkedHashMap<UUID, DocumentoFirma> porId = new LinkedHashMap<>();
        documentoFirmaRepository.findByNombreContainingIgnoreCase(texto)
                .forEach(d -> porId.put(d.getId(), d));

        if (porId.size() < LIMITE_POR_CATEGORIA) {
            for (DocumentoFirmante firmante : documentoFirmanteRepository.findByNombreCapturadoContainingIgnoreCase(texto)) {
                if (porId.size() >= LIMITE_POR_CATEGORIA) {
                    break;
                }
                DocumentoFirma doc = firmante.getDocumento();
                if (doc != null) {
                    porId.putIfAbsent(doc.getId(), doc);
                }
            }
        }

        return porId.values().stream()
                .limit(LIMITE_POR_CATEGORIA)
                .map(d -> new ResultadoBusqueda("DOCUMENTO", d.getId().toString(), d.getNombre(),
                        "Documento para firma", "/admin/documentos/" + d.getId()))
                .toList();
    }

    private String rutaConQuery(String base, String texto) {
        return base + "?q=" + URLEncoder.encode(texto, StandardCharsets.UTF_8);
    }
}
