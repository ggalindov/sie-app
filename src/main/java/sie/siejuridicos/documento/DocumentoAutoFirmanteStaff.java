package sie.siejuridicos.documento;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sie.siejuridicos.usuario.UsuarioInterno;

// Firma automática del gerente y de cada abogado que gerencia asignó a un documento: al
// tratarse de staff ya autenticado, cuya firma manuscrita ya fue cargada una vez en su
// perfil (ver "Mi firma"), no requiere OTP ni captura de identidad adicional -- a
// diferencia del firmante externo (ver DocumentoFirmante para partes). Deja constancia de
// la imagen de firma usada en el momento exacto del acto (no solo una referencia al
// perfil, que podría cambiar después).
//
// A diferencia de UCN (que resuelve "cuál gerente firma" buscando cualquier activo con
// firma cargada, porque solo existe un firmante de cada rol por documento), en SIE el
// firmante STAFF_GERENTE/STAFF_ABOGADO ya existe como fila PENDIENTE desde que gerencia
// armó la asignación (ver DocumentoFirmaService.crearFirmantesIniciales) -- este servicio
// solo cierra esa fila existente, nunca decide a quién firmar.
@Service
public class DocumentoAutoFirmanteStaff {

    private final DocumentoFirmanteRepository documentoFirmanteRepository;
    private final DocumentoTimelineService documentoTimelineService;

    public DocumentoAutoFirmanteStaff(DocumentoFirmanteRepository documentoFirmanteRepository,
                                       DocumentoTimelineService documentoTimelineService) {
        this.documentoFirmanteRepository = documentoFirmanteRepository;
        this.documentoTimelineService = documentoTimelineService;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void firmar(DocumentoFirmante firmante) {
        UsuarioInterno staff = firmante.getStaff();
        firmante.registrarFirma(staff.getFirmaPng(), null);
        documentoFirmanteRepository.save(firmante);
        String rolVisible = DocumentoFirmante.TIPO_STAFF_GERENTE.equals(firmante.getTipoFirmante()) ? "gerente" : "abogado";
        documentoTimelineService.registrarStaff(firmante.getDocumento(), DocumentoEvento.TIPO_FIRMADO_STAFF, staff,
                "Firmado por " + staff.getNombre() + " (" + rolVisible + ").");
    }
}
