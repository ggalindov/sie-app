package sie.siejuridicos.documento;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import sie.siejuridicos.common.exception.EntidadInvalidaException;
import sie.siejuridicos.documento.dto.AsignarParteRequest;
import sie.siejuridicos.documento.dto.CrearCambioSolicitudRequest;
import sie.siejuridicos.documento.dto.DocumentoCambioSolicitudDto;
import sie.siejuridicos.documento.dto.DocumentoFirmaListadoDto;
import sie.siejuridicos.documento.dto.DocumentoSeguimientoDto;
import sie.siejuridicos.documento.dto.RechazarCambioRequest;
import sie.siejuridicos.security.UsuarioInternoPrincipal;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

// Panel de administración del motor de firma -- pieza NUEVA (ver DocumentoAdminService).
// Subir/emitir un documento es exclusivo de gerencia (ADMIN_GENERAL, "el gerente puede
// asignar abogados... asigna las N partes"); listar/ver seguimiento/descargar está al
// alcance de ambos roles (un abogado necesita ver los documentos donde quedó como
// firmante). Las solicitudes de cambio (doble confirmación) también están al alcance de
// ambos: por diseño, una la crea o confirma un ADMIN_GENERAL y la otra un ABOGADO.
@RestController
@RequestMapping("/api/admin/documentos")
@PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ABOGADO')")
public class DocumentoAdminController {

    private final DocumentoAdminService documentoAdminService;
    private final DocumentChangeRequestService documentChangeRequestService;
    private final ObjectMapper objectMapper;

    public DocumentoAdminController(DocumentoAdminService documentoAdminService,
                                     DocumentChangeRequestService documentChangeRequestService,
                                     ObjectMapper objectMapper) {
        this.documentoAdminService = documentoAdminService;
        this.documentChangeRequestService = documentChangeRequestService;
        this.objectMapper = objectMapper;
    }

    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<DocumentoFirmaListadoDto> crear(@RequestParam("archivo") MultipartFile archivo,
                                                           @RequestParam("nombre") String nombre,
                                                           @RequestParam(value = "casoId", required = false) Long casoId,
                                                           @RequestParam(value = "abogadoIds", required = false) List<Long> abogadoIds,
                                                           @RequestParam(value = "partesJson", required = false) String partesJson,
                                                           @AuthenticationPrincipal UsuarioInternoPrincipal principal) {
        List<AsignarParteRequest> partes = leerPartes(partesJson);
        byte[] pdf = leerBytes(archivo);
        DocumentoFirmaListadoDto creado = documentoAdminService.crear(pdf, nombre, casoId, abogadoIds, partes, principal.getId());
        return ResponseEntity.ok(creado);
    }

    @GetMapping
    public ResponseEntity<List<DocumentoFirmaListadoDto>> listar(@RequestParam(required = false) String estado) {
        return ResponseEntity.ok(documentoAdminService.listar(estado));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentoSeguimientoDto> seguimiento(@PathVariable UUID id) {
        return ResponseEntity.ok(documentoAdminService.seguimiento(id));
    }

    @GetMapping("/{id}/descargar")
    public ResponseEntity<byte[]> descargar(@PathVariable UUID id,
                                             @RequestParam(value = "hojaCierre", defaultValue = "false") boolean hojaCierre) {
        byte[] pdf = documentoAdminService.descargar(id, hojaCierre);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("documento.pdf", StandardCharsets.UTF_8).build().toString())
                .body(pdf);
    }

    @GetMapping("/{id}/certificado-evidencia")
    public ResponseEntity<byte[]> certificadoEvidencia(@PathVariable UUID id) {
        byte[] pdf = documentoAdminService.certificadoEvidencia(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("certificado-evidencia.pdf", StandardCharsets.UTF_8).build().toString())
                .body(pdf);
    }

    // --- Solicitudes de cambio (doble confirmación ADMIN_GENERAL + ABOGADO) ---

    @PostMapping("/cambios")
    public ResponseEntity<DocumentoCambioSolicitudDto> crearCambio(@RequestBody CrearCambioSolicitudRequest request,
                                                                    @AuthenticationPrincipal UsuarioInternoPrincipal principal) {
        return ResponseEntity.ok(documentChangeRequestService.crear(request.documentoId(), request.tipoAccion(),
                request.justificacion(), request.payloadJson(), principal.getId()));
    }

    @GetMapping("/cambios")
    public ResponseEntity<List<DocumentoCambioSolicitudDto>> listarCambios(@RequestParam(required = false) String estado) {
        return ResponseEntity.ok(documentChangeRequestService.listarPorEstado(estado));
    }

    @PostMapping("/cambios/{id}/confirmar")
    public ResponseEntity<DocumentoCambioSolicitudDto> confirmarCambio(@PathVariable UUID id,
                                                                        @AuthenticationPrincipal UsuarioInternoPrincipal principal) {
        return ResponseEntity.ok(documentChangeRequestService.confirmar(id, principal.getId()));
    }

    @PostMapping("/cambios/{id}/rechazar")
    public ResponseEntity<DocumentoCambioSolicitudDto> rechazarCambio(@PathVariable UUID id,
                                                                       @RequestBody RechazarCambioRequest request,
                                                                       @AuthenticationPrincipal UsuarioInternoPrincipal principal) {
        return ResponseEntity.ok(documentChangeRequestService.rechazar(id, principal.getId(), request.motivo()));
    }

    @PostMapping("/cambios/{id}/ejecutar")
    public ResponseEntity<DocumentoCambioSolicitudDto> ejecutarCambio(@PathVariable UUID id,
                                                                       @AuthenticationPrincipal UsuarioInternoPrincipal principal) {
        return ResponseEntity.ok(documentChangeRequestService.ejecutar(id, principal.getId()));
    }

    private List<AsignarParteRequest> leerPartes(String partesJson) {
        if (partesJson == null || partesJson.isBlank()) {
            return List.of();
        }
        try {
            return List.of(objectMapper.readValue(partesJson, AsignarParteRequest[].class));
        } catch (Exception ex) {
            throw new EntidadInvalidaException("La lista de partes no es un JSON válido.");
        }
    }

    private byte[] leerBytes(MultipartFile archivo) {
        try {
            return archivo.getBytes();
        } catch (IOException ex) {
            throw new EntidadInvalidaException("No se pudo leer el archivo enviado.");
        }
    }
}
