package sie.siejuridicos.marketing;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sie.siejuridicos.common.excel.ExcelExporter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// Reglas de rol reforzadas también en SecurityConfig (/api/admin/marketing/** es solo
// ADMIN_GENERAL, ver SecurityConfig): @PreAuthorize queda como segunda capa de defensa
// a nivel de método, y aquí refleja la misma restricción (antes decía ADMIN_GENERAL Y
// ABOGADO, ya desactualizado desde que se restringió marketing solo al administrador).
@RestController
@RequestMapping("/api/admin/marketing/suscriptores")
@PreAuthorize("hasRole('ADMIN_GENERAL')")
public class SuscriptorMarketingController {

    private final SuscriptorMarketingRepository suscriptorMarketingRepository;

    public SuscriptorMarketingController(SuscriptorMarketingRepository suscriptorMarketingRepository) {
        this.suscriptorMarketingRepository = suscriptorMarketingRepository;
    }

    @GetMapping
    public ResponseEntity<List<SuscriptorMarketingResponse>> listar() {
        return ResponseEntity.ok(suscriptorMarketingRepository.findByActivoTrueOrderByFechaSuscripcionDesc().stream()
                .map(SuscriptorMarketingResponse::desde)
                .toList());
    }

    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportar() {
        List<SuscriptorMarketing> suscriptores = suscriptorMarketingRepository.findByActivoTrueOrderByFechaSuscripcionDesc();

        List<List<Object>> filas = new ArrayList<>();
        for (SuscriptorMarketing s : suscriptores) {
            filas.add(List.of(s.getNombre(), s.getCorreo(), s.getFechaSuscripcion()));
        }

        byte[] archivo = ExcelExporter.generar(
                "Suscriptores",
                List.of("Nombre", "Correo", "Fecha de suscripción"),
                filas
        );

        String nombreArchivo = "suscriptores-marketing-" + LocalDate.now() + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nombreArchivo).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(archivo);
    }
}
