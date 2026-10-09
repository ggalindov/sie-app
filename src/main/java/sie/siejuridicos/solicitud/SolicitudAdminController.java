package sie.siejuridicos.solicitud;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sie.siejuridicos.common.excel.ExcelExporter;
import sie.siejuridicos.security.UsuarioInternoPrincipal;
import sie.siejuridicos.solicitud.dto.ActualizarEstadoSolicitudRequest;
import sie.siejuridicos.solicitud.dto.AgendarCitaRequest;
import sie.siejuridicos.solicitud.dto.CrearSolicitudDirectaRequest;
import sie.siejuridicos.solicitud.dto.ResponderSolicitudRequest;
import sie.siejuridicos.solicitud.dto.ResponsableReunionResponse;
import sie.siejuridicos.solicitud.dto.SolicitudResponse;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

// Reglas de rol reforzadas también en SecurityConfig (/api/admin/**); @PreAuthorize
// queda como segunda capa de defensa a nivel de método.
@RestController
@RequestMapping("/api/admin/solicitudes")
@PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ABOGADO')")
public class SolicitudAdminController {

    private final SolicitudService solicitudService;

    public SolicitudAdminController(SolicitudService solicitudService) {
        this.solicitudService = solicitudService;
    }

    // Crea una solicitud directamente desde el panel (Calendario), para poder agendarle una
    // reunión a un cliente que no llegó por el formulario público -- un caso ya existente en
    // el sistema, o alguien completamente nuevo (ver SolicitudService.crearDirecta).
    @PostMapping("/directa")
    public ResponseEntity<SolicitudResponse> crearDirecta(@Valid @RequestBody CrearSolicitudDirectaRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(solicitudService.crearDirecta(request));
    }

    @GetMapping
    public ResponseEntity<List<SolicitudResponse>> listar(
            @RequestParam(required = false) EstadoSolicitud estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return ResponseEntity.ok(solicitudService.listar(estado, desde, hasta));
    }

    // Calendario visual de reuniones: citas dentro de [desde, hasta], acotadas por rol (ver
    // SolicitudService.listarCalendario) -- ADMIN_GENERAL puede además filtrar por un
    // abogado puntual con abogadoId, un ABOGADO lo ignora y siempre ve solo lo suyo.
    @GetMapping("/calendario")
    public ResponseEntity<List<SolicitudResponse>> calendario(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long abogadoId,
            @AuthenticationPrincipal UsuarioInternoPrincipal principal) {
        return ResponseEntity.ok(solicitudService.listarCalendario(desde, hasta, abogadoId, principal));
    }

    // Usuarios internos activos entre los que se puede elegir responsable al agendar (ver
    // AgendarCitaRequest.responsablesIds) -- alimenta el selector del calendario, el filtro
    // por abogado de ADMIN_GENERAL y el multi-select de corresponsables. Antes esta ruta
    // quedaba restringida solo a ADMIN_GENERAL (un ABOGADO siempre quedaba asignado a sí
    // mismo, nunca elegía), pero ahora un ABOGADO también necesita ver esta lista para poder
    // sumar colegas como corresponsables de su propia reunión -- por eso hereda el permiso de
    // clase (ADMIN_GENERAL o ABOGADO) en vez de restringirse más.
    @GetMapping("/responsables")
    public ResponseEntity<List<ResponsableReunionResponse>> responsables() {
        return ResponseEntity.ok(solicitudService.listarResponsables());
    }

    // Mismos filtros que /listar (estado/desde/hasta): descarga exactamente lo que se
    // está viendo en pantalla, no siempre la tabla completa.
    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportar(
            @RequestParam(required = false) EstadoSolicitud estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        List<SolicitudResponse> solicitudes = solicitudService.listar(estado, desde, hasta);

        List<List<Object>> filas = new ArrayList<>();
        for (SolicitudResponse s : solicitudes) {
            filas.add(List.of(
                    s.nombre(),
                    s.correo(),
                    s.telefono() != null ? s.telefono() : "",
                    s.mensaje(),
                    s.origen().name(),
                    s.estado().name(),
                    s.notasInternas() != null ? s.notasInternas() : "",
                    s.fechaCreacion(),
                    s.fechaCita() != null ? s.fechaCita() : "",
                    s.responsables().stream().map(ResponsableReunionResponse::nombre).collect(Collectors.joining(", ")),
                    s.fechaCita() != null ? s.tipoReunion().name() : "",
                    s.tipoReunion() == TipoReunion.PRESENCIAL
                            ? (s.lugarReunion() != null ? s.lugarReunion() : "")
                            : (s.linkReunion() != null ? s.linkReunion() : "")
            ));
        }

        byte[] archivo = ExcelExporter.generar(
                "Solicitudes",
                List.of("Nombre", "Correo", "Teléfono", "Mensaje", "Origen", "Estado",
                        "Notas internas", "Fecha de creación", "Fecha de cita", "Responsable de la reunión",
                        "Tipo de reunión", "Acceso a la reunión (link o lugar)"),
                filas
        );

        String nombreArchivo = "solicitudes-" + LocalDate.now() + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nombreArchivo).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(archivo);
    }

    @PatchMapping("/{id}/estado")
    public ResponseEntity<SolicitudResponse> actualizarEstado(@PathVariable Long id,
                                                                @Valid @RequestBody ActualizarEstadoSolicitudRequest request) {
        return ResponseEntity.ok(solicitudService.actualizarEstado(id, request.nuevoEstado()));
    }

    @PatchMapping("/{id}/cita")
    public ResponseEntity<SolicitudResponse> agendarCita(@PathVariable Long id,
                                                           @Valid @RequestBody AgendarCitaRequest request,
                                                           @AuthenticationPrincipal UsuarioInternoPrincipal principal) {
        return ResponseEntity.ok(solicitudService.agendarCita(id, request, principal));
    }

    // Buzón de respuesta directa de una solicitud (ver SolicitudService.responder): el
    // abogado/admin escribe asunto y mensaje en el panel, el backend arma el correo con el
    // formato de marca de la firma y lo manda al correo del cliente con copia obligatoria a
    // gerencia (ver EmailService.enviarRespuestaSolicitud / app.correo.gerencia).
    @PostMapping("/{id}/responder")
    public ResponseEntity<Void> responder(@PathVariable Long id,
                                            @Valid @RequestBody ResponderSolicitudRequest request) {
        solicitudService.responder(id, request.asunto(), request.mensaje());
        // noContent() (204), no ok().build() (200 con cuerpo vacío): bug real encontrado en
        // producción -- pedido<T>() en el frontend (admin-api.ts) solo trata como "sin cuerpo"
        // un 204; con 200 intentaba res.json() sobre una respuesta vacía, eso lanzaba
        // "Unexpected end of JSON input" DESPUÉS de que el correo ya se había enviado de
        // verdad, y el admin veía "No se pudo enviar la respuesta" en un envío que sí llegó.
        return ResponseEntity.noContent().build();
    }

    // Eliminación permanente de una solicitud (ver SolicitudService.eliminar) -- requiere que
    // el admin/abogado escriba literalmente "ELIMINAR" en el panel antes de poder confirmar
    // (ver ConfirmarEliminarModal en el frontend); aun así, a nivel de API esto es un DELETE
    // real sin ninguna palabra de por medio: la confirmación de la palabra es una barrera de
    // UI, no algo que el backend pueda o deba validar.
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        solicitudService.eliminar(id);
        return ResponseEntity.noContent().build();
    }
}
