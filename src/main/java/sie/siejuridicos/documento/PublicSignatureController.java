package sie.siejuridicos.documento;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import sie.siejuridicos.documento.dto.AdvertenciaSuplantacionRequest;
import sie.siejuridicos.documento.dto.ConsentimientoRequest;
import sie.siejuridicos.documento.dto.DocumentDownload;
import sie.siejuridicos.documento.dto.FirmaResumenDto;
import sie.siejuridicos.documento.dto.GeolocalizacionRequest;
import sie.siejuridicos.documento.dto.OtpVerificarRequest;
import sie.siejuridicos.documento.dto.RechazarFirmaRequest;
import sie.siejuridicos.documento.dto.VerificarIdentidadRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

// Flujo PÚBLICO de firma electrónica: sin JWT de staff, protegido únicamente por el token
// opaco del link único (ver SignerTokenService) más el rate limiting de RateLimitFilter y
// la lista blanca de SecurityConfig. Cada llamada resuelve el token de nuevo (no hay
// sesión): un token vencido, ya usado o inválido siempre responde 401, nunca revela
// información adicional.
@RestController
@RequestMapping("/api/v1/firmas")
public class PublicSignatureController {

    private final PublicSignatureService publicSignatureService;
    private final ProxyHeuristicsAnalyzer proxyHeuristicsAnalyzer;
    private final DocumentoVistaPreviaService documentoVistaPreviaService;

    public PublicSignatureController(PublicSignatureService publicSignatureService,
                                      ProxyHeuristicsAnalyzer proxyHeuristicsAnalyzer,
                                      DocumentoVistaPreviaService documentoVistaPreviaService) {
        this.publicSignatureService = publicSignatureService;
        this.proxyHeuristicsAnalyzer = proxyHeuristicsAnalyzer;
        this.documentoVistaPreviaService = documentoVistaPreviaService;
    }

    @GetMapping("/{token}")
    public ResponseEntity<FirmaResumenDto> resumen(@PathVariable String token) {
        return ResponseEntity.ok(publicSignatureService.resumen(token));
    }

    @PostMapping("/{token}/verificar-identidad")
    public ResponseEntity<FirmaResumenDto> verificarIdentidad(@PathVariable String token,
                                                               @Valid @RequestBody VerificarIdentidadRequest request,
                                                               HttpServletRequest httpRequest) {
        publicSignatureService.verificarIdentidad(token, request.numeroDocumento(),
                ClientIpResolver.resolver(httpRequest), httpRequest.getHeader("User-Agent"));
        return ResponseEntity.ok(publicSignatureService.resumen(token));
    }

    @PostMapping("/{token}/rechazar")
    public ResponseEntity<FirmaResumenDto> rechazar(@PathVariable String token, @Valid @RequestBody RechazarFirmaRequest request,
                                                     HttpServletRequest httpRequest) {
        publicSignatureService.rechazar(token, request.motivo(), ClientIpResolver.resolver(httpRequest),
                httpRequest.getHeader("User-Agent"));
        return ResponseEntity.ok(publicSignatureService.resumen(token));
    }

    @PostMapping("/{token}/otp/reenviar")
    public ResponseEntity<Void> reenviarOtp(@PathVariable String token, HttpServletRequest request) {
        publicSignatureService.reenviarOtp(token, ClientIpResolver.resolver(request));
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/{token}/otp/verificar")
    public ResponseEntity<FirmaResumenDto> verificarOtp(@PathVariable String token, @Valid @RequestBody OtpVerificarRequest request) {
        publicSignatureService.verificarOtp(token, request.codigo());
        return ResponseEntity.ok(publicSignatureService.resumen(token));
    }

    @PostMapping("/{token}/consentimiento")
    public ResponseEntity<FirmaResumenDto> consentimiento(@PathVariable String token, @Valid @RequestBody ConsentimientoRequest request) {
        publicSignatureService.registrarConsentimiento(token, request);
        return ResponseEntity.ok(publicSignatureService.resumen(token));
    }

    @PostMapping("/{token}/geolocalizacion")
    public ResponseEntity<FirmaResumenDto> geolocalizacion(@PathVariable String token, @RequestBody GeolocalizacionRequest request) {
        publicSignatureService.registrarGeolocalizacion(token, request);
        return ResponseEntity.ok(publicSignatureService.resumen(token));
    }

    @PostMapping("/{token}/advertencia-identidad")
    public ResponseEntity<FirmaResumenDto> advertenciaSuplantacionIdentidad(@PathVariable String token,
                                                                             @RequestBody AdvertenciaSuplantacionRequest request) {
        publicSignatureService.aceptarAdvertenciaSuplantacionIdentidad(token, request.aceptar());
        return ResponseEntity.ok(publicSignatureService.resumen(token));
    }

    @PostMapping("/{token}/advertencia-verificacion")
    public ResponseEntity<FirmaResumenDto> advertenciaSuplantacionVerificacion(@PathVariable String token,
                                                                                @RequestBody AdvertenciaSuplantacionRequest request) {
        publicSignatureService.aceptarAdvertenciaSuplantacionVerificacion(token, request.aceptar());
        return ResponseEntity.ok(publicSignatureService.resumen(token));
    }

    @PostMapping(value = "/{token}/identidad", consumes = "multipart/form-data")
    public ResponseEntity<FirmaResumenDto> identidad(@PathVariable String token, @RequestParam("archivo") MultipartFile archivo) {
        publicSignatureService.registrarIdentidad(token, leerBytes(archivo), archivo.getContentType());
        return ResponseEntity.ok(publicSignatureService.resumen(token));
    }

    @PostMapping(value = "/{token}/verificacion-humana", consumes = "multipart/form-data")
    public ResponseEntity<FirmaResumenDto> verificacionHumana(@PathVariable String token, @RequestParam("archivo") MultipartFile archivo) {
        publicSignatureService.registrarVerificacionHumana(token, leerBytes(archivo), archivo.getContentType());
        return ResponseEntity.ok(publicSignatureService.resumen(token));
    }

    @PostMapping(value = "/{token}/firmar", consumes = "multipart/form-data")
    public ResponseEntity<FirmaResumenDto> firmar(@PathVariable String token, @RequestParam("firma") MultipartFile firma,
                                                   @RequestParam(name = "huellaDispositivo", required = false) String huellaDispositivo,
                                                   @RequestParam(name = "contextoDispositivoJson", required = false) String contextoDispositivoJson,
                                                   @RequestParam(name = "trazoVectorialJson", required = false) String trazoVectorialJson,
                                                   HttpServletRequest request) {
        String senalesProxy = proxyHeuristicsAnalyzer.analizar(request);
        publicSignatureService.firmar(token, leerBytes(firma), ClientIpResolver.resolver(request), request.getHeader("User-Agent"),
                huellaDispositivo, contextoDispositivoJson, senalesProxy, trazoVectorialJson);
        return ResponseEntity.ok(publicSignatureService.resumen(token));
    }

    @GetMapping("/{token}/certificado-evidencia")
    public ResponseEntity<byte[]> certificadoEvidencia(@PathVariable String token, HttpServletRequest httpRequest) {
        byte[] pdf = publicSignatureService.certificadoEvidencia(token, ClientIpResolver.resolver(httpRequest),
                httpRequest.getHeader("User-Agent"));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("certificado-evidencia.pdf", StandardCharsets.UTF_8).build().toString())
                .body(pdf);
    }

    // Vista previa SIN descarga: ningún endpoint entrega el PDF a quien está firmando
    // antes de que termine; solo cuántas páginas tiene y cada página como imagen con
    // marca de agua (ver DocumentoVistaPreviaService). Mismos requisitos que antes:
    // identidad y código verificados.
    @GetMapping("/{token}/documento/paginas")
    public ResponseEntity<Map<String, Integer>> paginasDelDocumento(@PathVariable String token, HttpServletRequest httpRequest) {
        DocumentDownload documento = publicSignatureService.documentoParaRevisar(token);
        // Se registra aquí, no dentro de documentoParaRevisar: este endpoint es el primer
        // paso de cada sesión de revisión (pide el conteo de páginas una sola vez), a
        // diferencia de paginaDelDocumento (abajo), que se llama una vez POR página.
        publicSignatureService.registrarRevisionConsultada(token, ClientIpResolver.resolver(httpRequest), httpRequest.getHeader("User-Agent"));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Map.of("paginas", documentoVistaPreviaService.contarPaginas(documento.contenido())));
    }

    @GetMapping("/{token}/documento/paginas/{numero}")
    public ResponseEntity<byte[]> paginaDelDocumento(@PathVariable String token, @PathVariable int numero) {
        DocumentDownload documento = publicSignatureService.documentoParaRevisar(token);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(documentoVistaPreviaService.renderizarPagina(documento.contenido(), numero));
    }

    @GetMapping("/{token}/documento-final")
    public ResponseEntity<byte[]> documentoFinal(@PathVariable String token, HttpServletRequest httpRequest) {
        DocumentDownload descarga = publicSignatureService.documentoFinal(token, ClientIpResolver.resolver(httpRequest),
                httpRequest.getHeader("User-Agent"));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(descarga.nombreArchivo(), StandardCharsets.UTF_8).build().toString())
                .body(descarga.contenido());
    }

    private byte[] leerBytes(MultipartFile archivo) {
        try {
            return archivo.getBytes();
        } catch (IOException ex) {
            throw new IllegalArgumentException("No se pudo leer el archivo enviado.", ex);
        }
    }
}
