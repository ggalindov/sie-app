package sie.siejuridicos.caso;

import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sie.siejuridicos.caso.dto.ActualizarCasoRequest;
import sie.siejuridicos.caso.dto.CasoAdminResponse;
import sie.siejuridicos.caso.dto.CrearCasoRequest;
import sie.siejuridicos.caso.dto.ResumenEnvioCorreos;
import sie.siejuridicos.caso.dto.ResumenReporteSemanal;
import sie.siejuridicos.caso.dto.ResumenSincronizacion;
import sie.siejuridicos.common.exception.RecursoNoEncontradoException;
import sie.siejuridicos.security.UsuarioInternoPrincipal;
import sie.siejuridicos.vigilancia.BoletinJudicialCacheService;
import sie.siejuridicos.vigilancia.DocumentoProvidenciaCandidato;
import sie.siejuridicos.vigilancia.EnvioDocumentoJudicialService;
import sie.siejuridicos.vigilancia.PublicacionesProcesalesClient;
import sie.siejuridicos.vigilancia.VigilanciaJudicialService;
import sie.siejuridicos.vigilancia.dto.BuscarDocumentosBoletinRequest;
import sie.siejuridicos.vigilancia.dto.DocumentoRecienteResponse;
import sie.siejuridicos.vigilancia.dto.EnviarDocumentoJudicialRequest;
import sie.siejuridicos.vigilancia.dto.EnvioDocumentoJudicialResponse;
import sie.siejuridicos.vigilancia.dto.TextoDocumentoJudicialResponse;
import sie.siejuridicos.vigilancia.dto.VerTextoDocumentoRequest;

import java.util.List;

// Gestión de casos: alcance de ambos roles (como Solicitudes), lo crea "el abogado o el
// administrador" según lo pedido; hereda hasAnyRole(ADMIN_GENERAL, ABOGADO) de
// /api/admin/** en SecurityConfig, sin restricción adicional.
//
// Ya no hay endpoint de cambiar etapa: el estado del caso se lee en vivo del Google Sheets de
// la firma (ver HojaCalculoService), no se gestiona desde aquí.
@RestController
@RequestMapping("/api/admin/casos")
@PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ABOGADO')")
public class CasoAdminController {

    private final CasoService casoService;
    private final CasoRepository casoRepository;
    private final VigilanciaJudicialService vigilanciaJudicialService;
    private final EnvioDocumentoJudicialService envioDocumentoJudicialService;
    private final PublicacionesProcesalesClient publicacionesProcesalesClient;
    private final BoletinJudicialCacheService boletinJudicialCacheService;

    public CasoAdminController(CasoService casoService, CasoRepository casoRepository,
                                VigilanciaJudicialService vigilanciaJudicialService,
                                EnvioDocumentoJudicialService envioDocumentoJudicialService,
                                PublicacionesProcesalesClient publicacionesProcesalesClient,
                                BoletinJudicialCacheService boletinJudicialCacheService) {
        this.casoService = casoService;
        this.casoRepository = casoRepository;
        this.vigilanciaJudicialService = vigilanciaJudicialService;
        this.envioDocumentoJudicialService = envioDocumentoJudicialService;
        this.publicacionesProcesalesClient = publicacionesProcesalesClient;
        this.boletinJudicialCacheService = boletinJudicialCacheService;
    }

    // Respaldo manual: el flujo principal ahora es sincronizar() (ver más abajo).
    @PostMapping
    public ResponseEntity<CasoAdminResponse> crear(
            @Valid @RequestBody CrearCasoRequest request, @AuthenticationPrincipal UsuarioInternoPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(casoService.crear(request, principal != null ? principal.getUsuario() : null));
    }

    @GetMapping
    public ResponseEntity<List<CasoAdminResponse>> listar(@AuthenticationPrincipal UsuarioInternoPrincipal principal) {
        return ResponseEntity.ok(casoService.listarTodos(principal != null ? principal.getId() : null));
    }

    // Bóveda de Casos Privados y Cifrados. ADMIN_GENERAL ve la de Gerencia, un ABOGADO ve SU
    // PROPIA bóveda personal (pedido explícito del usuario, 2026-11-05: "implementa la misma
    // funcionalidad... para todos los abogados") -- el filtrado real ocurre en
    // CasoService.listarPrivados(), nunca se mezclan.
    @GetMapping("/privados")
    public ResponseEntity<List<CasoAdminResponse>> listarPrivados(@AuthenticationPrincipal UsuarioInternoPrincipal principal) {
        return ResponseEntity.ok(casoService.listarPrivados(principal != null ? principal.getId() : null));
    }

    @PatchMapping("/{id}/marcar-privado")
    public ResponseEntity<CasoAdminResponse> marcarPrivado(
            @PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestParam boolean esPrivado,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String motivo,
            @AuthenticationPrincipal UsuarioInternoPrincipal principal) {
        return ResponseEntity.ok(casoService.marcarPrivado(id, esPrivado, motivo, principal.getUsuario()));
    }

    // Pedido explícito del usuario (2026-11-04, revierte la decisión original de "solo
    // administrador" de esta misma sesión de trabajo): "permite que todos los gerentes y
    // abogados puedan editar la informacion de un caso" -- ahora mismo nivel de acceso que el
    // resto de este controlador (ADMIN_GENERAL o ABOGADO por igual, ver el @PreAuthorize de
    // la clase).
    @PatchMapping("/{id}")
    public ResponseEntity<CasoAdminResponse> actualizar(
            @PathVariable Long id, @RequestBody ActualizarCasoRequest request,
            @AuthenticationPrincipal UsuarioInternoPrincipal principal) {
        return ResponseEntity.ok(casoService.actualizar(id, request, principal.getUsuario()));
    }

    // Pedido explícito del usuario (2026-11-05): "un boton para cada caso el cual nos permita
    // finalizarlo, y el caso se remite a un nuevo apartado de casos finalizados". Mismo nivel
    // de acceso que editar un caso (ADMIN_GENERAL o ABOGADO, ver el @PreAuthorize de la clase).
    @PatchMapping("/{id}/finalizar")
    public ResponseEntity<CasoAdminResponse> finalizar(
            @PathVariable Long id, @AuthenticationPrincipal UsuarioInternoPrincipal principal) {
        return ResponseEntity.ok(casoService.finalizar(id, principal.getUsuario()));
    }

    // Inverso de finalizar(), por si se finalizó un caso por error.
    @PatchMapping("/{id}/reabrir")
    public ResponseEntity<CasoAdminResponse> reabrir(
            @PathVariable Long id, @AuthenticationPrincipal UsuarioInternoPrincipal principal) {
        return ResponseEntity.ok(casoService.reabrir(id, principal.getUsuario()));
    }

    // Nuevo apartado "Casos Finalizados" -- solo lectura, el "último seguimiento" guardado
    // queda intacto (nunca se vuelve a actualizar, ver CasoService.listarFinalizados()).
    @GetMapping("/finalizados")
    public ResponseEntity<List<CasoAdminResponse>> listarFinalizados() {
        return ResponseEntity.ok(casoService.listarFinalizados());
    }

    // Pedido explícito del usuario (2026-11-09): popup de "casos nuevos identificados" desde
    // el Google Sheets -- mismo nivel de acceso que el resto del controlador (gerente o
    // abogado).
    @GetMapping("/pendientes-aprobacion")
    public ResponseEntity<List<CasoAdminResponse>> listarPendientesAprobacion() {
        return ResponseEntity.ok(casoService.listarPendientesAprobacion());
    }

    @PostMapping("/{id}/aprobar")
    public ResponseEntity<CasoAdminResponse> aprobarNuevo(
            @PathVariable Long id, @AuthenticationPrincipal UsuarioInternoPrincipal principal) {
        return ResponseEntity.ok(casoService.aprobarCasoNuevo(id, principal.getUsuario()));
    }

    // Popup de "casos con cambios detectados" (radicado, nombre o abogado corregidos en la
    // hoja sobre un caso que ya existía) -- ver CasoCambioDetectado.
    @GetMapping("/cambios-pendientes")
    public ResponseEntity<List<sie.siejuridicos.caso.dto.CasoCambioDetectadoResponse>> listarCambiosPendientes() {
        return ResponseEntity.ok(casoService.listarCambiosPendientes());
    }

    @PostMapping("/cambios-pendientes/revisar")
    public ResponseEntity<Void> marcarCambiosRevisados(
            @RequestBody sie.siejuridicos.caso.dto.MarcarCambiosRevisadosRequest request,
            @AuthenticationPrincipal UsuarioInternoPrincipal principal) {
        casoService.marcarCambiosRevisados(request.ids(), principal.getUsuario());
        return ResponseEntity.noContent().build();
    }

    // Botón "Actualizar" del panel: trae/actualiza todos los casos desde el Google Sheets de
    // la firma, sin necesidad de cargarlos uno por uno.
    @PostMapping("/sincronizar")
    public ResponseEntity<ResumenSincronizacion> sincronizar() {
        return ResponseEntity.ok(casoService.sincronizarDesdeHoja());
    }

    // Botón "Enviar correos pendientes" del panel: notifica de una sola vez a todos los
    // clientes con radicado asignado que todavía no recibieron su correo. Pedido explícito
    // del usuario (2026-11-06): solo ADMIN_GENERAL -- comunicación masiva a TODOS los
    // clientes de la firma, no una acción operativa del día a día de un abogado individual.
    @PostMapping("/enviar-pendientes")
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<ResumenEnvioCorreos> enviarPendientes() {
        return ResponseEntity.ok(casoService.enviarCorreosPendientes());
    }

    // Disparo manual del reporte semanal (además del automático de los lunes, ver
    // ReporteSemanalCasosScheduler) -- para probarlo o adelantarlo sin esperar al próximo
    // ciclo. Mismo criterio que enviarPendientes: solo ADMIN_GENERAL.
    @PostMapping("/enviar-reporte-semanal")
    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    public ResponseEntity<ResumenReporteSemanal> enviarReporteSemanal() {
        return ResponseEntity.ok(casoService.enviarReporteSemanal());
    }

    // Vista PREVIA de solo lectura (ver VigilanciaJudicialService): consulta en vivo la Rama
    // Judicial y el portal de Publicaciones Procesales para mostrar la última decisión de
    // fondo y sus documentos candidatos. NUNCA envía nada a nadie -- no existe todavía (a
    // propósito) ningún endpoint que dispare un envío real a partir de esto, ver memoria del
    // proyecto (vigilancia-judicial-alcance.md).
    @GetMapping("/{id}/documento-reciente")
    public ResponseEntity<DocumentoRecienteResponse> documentoReciente(@PathVariable Long id) {
        Caso caso = casoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Caso no encontrado"));
        if (caso.getRadicadoId() == null || caso.getRadicadoId().isBlank()) {
            return ResponseEntity.ok(
                    DocumentoRecienteResponse.noDisponible("Este caso todavía no tiene radicado asignado."));
        }
        return ResponseEntity.ok(vigilanciaJudicialService.buscarUltimoDocumentoFundamental(caso.getRadicadoId()));
    }

    // Pedido explícito del usuario: "un botón por cada suceso directo" -- no solo la última
    // actuación de fondo (ver documentoReciente() arriba), sino CUALQUIER evento de la línea
    // de tiempo. fechaRegistro/nombreActuacion ya vienen del propio evento que el panel
    // muestra (ver ActuacionRegistradaResponse) -- esto NO vuelve a consultar la Rama
    // Judicial (CPNU), solo Publicaciones Procesales con los datos que ya tenemos guardados.
    //
    // Bug real reportado por el usuario: "en ninguno de los casos con boletin disponible sale
    // el boton azul" -- esta consulta EN VIVO nunca escribía en BoletinJudicialCache (solo lo
    // hacía el ciclo automático de las 7am, ver BoletinJudicialScheduler), así que el punto
    // azul del listado de /admin/casos se quedaba apagado para cualquier caso que el admin ya
    // hubiera revisado a mano, aunque sí hubiera boletines reales disponibles. Ahora, cuando
    // la búsqueda encuentra candidatos, también se cachean aquí mismo (ver
    // cachearDesdeConsultaManual) -- best-effort y fuera del camino de respuesta: un fallo de
    // caché (ej. Publicaciones Procesales cortó la descarga a mitad) nunca debe impedir que el
    // admin vea los candidatos que sí se pudieron listar.
    @PostMapping("/{id}/documentos-boletin")
    public ResponseEntity<List<DocumentoProvidenciaCandidato>> documentosBoletin(
            @PathVariable Long id, @RequestBody BuscarDocumentosBoletinRequest request) {
        Caso caso = casoRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Caso no encontrado"));
        if (caso.getRadicadoId() == null || caso.getRadicadoId().isBlank()) {
            return ResponseEntity.ok(List.of());
        }
        List<DocumentoProvidenciaCandidato> candidatos = publicacionesProcesalesClient.buscarDocumentos(
                caso.getRadicadoId(), request.fechaRegistro(), request.nombreActuacion());
        try {
            boletinJudicialCacheService.cachearDesdeConsultaManual(caso, candidatos);
        } catch (Exception ex) {
            // Silencioso a propósito (ver comentario de arriba): la caché es una mejora, no
            // una condición para responder la búsqueda.
        }
        return ResponseEntity.ok(candidatos);
    }

    // El ÚNICO endpoint de todo el sistema que puede terminar mandándole un correo real a un
    // cliente a partir de la vigilancia judicial -- siempre manual, el abogado elige UNO de
    // los candidatos que ya vio en documentoReciente() y lo confirma (ver
    // EnvioDocumentoJudicialService para las reglas: respeta app.bloqueo-total-clientes,
    // valida el dominio del documento antes de descargarlo).
    @PostMapping("/{id}/documento-reciente/enviar")
    public ResponseEntity<EnvioDocumentoJudicialResponse> enviarDocumentoReciente(
            @PathVariable Long id, @RequestBody EnviarDocumentoJudicialRequest request) {
        return ResponseEntity.ok(envioDocumentoJudicialService.enviar(id, request));
    }

    // Pedido explícito del usuario: el "anotacion" corto que trae el CPNU ("DESCORRE",
    // "MEMORIAL PODER") NO es el texto real del Estado Electrónico -- esto extrae el texto
    // VERBATIM del PDF oficial (ver PublicacionesProcesalesClient.descargarYExtraerTexto()).
    // Solo lectura, bajo demanda sobre UN documento puntual ya elegido de la lista de
    // candidatos -- nunca automático para toda la línea de tiempo.
    @PostMapping("/{id}/documento-reciente/texto")
    public ResponseEntity<TextoDocumentoJudicialResponse> textoDocumentoReciente(
            @PathVariable Long id, @RequestBody VerTextoDocumentoRequest request) {
        return publicacionesProcesalesClient.descargarYExtraerTexto(request.urlDocumento())
                .map(texto -> ResponseEntity.ok(TextoDocumentoJudicialResponse.disponible(texto)))
                .orElseGet(() -> ResponseEntity.ok(TextoDocumentoJudicialResponse.noDisponible(
                        "No se pudo descargar o leer el texto de este documento -- intenta de nuevo en unos minutos.")));
    }

    // Pedido explícito del usuario: además del texto extraído, poder ver/abrir el PDF real
    // -- mismo documento ya validado por host en PublicacionesProcesalesClient, nunca una URL
    // arbitraria. inline (no attachment): se abre en el visor del navegador, no se fuerza una
    // descarga a disco.
    @PostMapping("/{id}/documento-reciente/pdf")
    public ResponseEntity<byte[]> pdfDocumentoReciente(
            @PathVariable Long id, @RequestBody VerTextoDocumentoRequest request) {
        byte[] pdf = publicacionesProcesalesClient.descargarDocumento(request.urlDocumento())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No se pudo descargar este documento de Publicaciones Procesales."));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename("providencia.pdf").build().toString())
                .body(pdf);
    }
}
