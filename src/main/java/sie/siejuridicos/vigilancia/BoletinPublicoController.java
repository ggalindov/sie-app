package sie.siejuridicos.vigilancia;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sie.siejuridicos.common.exception.RecursoNoEncontradoException;

// Link PÚBLICO de un solo documento para los correos de vigilancia judicial (gerencia,
// abogados y clientes) -- sin JWT, protegido únicamente por el token opaco de la fila (ver
// BoletinJudicialCache.tokenPublico), mismo criterio que PublicSignatureController. Pedido
// explícito del usuario, 2026-11-04, tras un correo real con 149 archivos PDF adjuntos
// ("no se puede enviar asi todos los autos/boletines... mas bien por cada fila que tenga
// el linkcito directo al documento dentro de nuestro servdor"): el documento YA vive en
// nuestro propio servidor (BoletinJudicialCache.contenidoBytes), así que el correo ya no
// adjunta ningún PDF, solo enlaza aquí por fila.
@RestController
@RequestMapping("/api/v1/boletines")
public class BoletinPublicoController {

    private final BoletinJudicialCacheRepository boletinRepository;

    public BoletinPublicoController(BoletinJudicialCacheRepository boletinRepository) {
        this.boletinRepository = boletinRepository;
    }

    @GetMapping("/documento/{token}")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> descargarPorToken(@PathVariable String token) {
        BoletinJudicialCache boletin = boletinRepository.findByTokenPublico(token)
                .orElseThrow(() -> new RecursoNoEncontradoException("Documento no encontrado"));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(boletin.getNombreArchivo()).build().toString())
                .body(boletin.getContenidoBytes());
    }
}
