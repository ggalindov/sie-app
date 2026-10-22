package sie.siejuridicos.documento;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import sie.siejuridicos.usuario.UsuarioInterno;

import java.time.LocalDateTime;
import java.util.UUID;

// Snapshot binario de un documento antes de que quede SELLADO: cuando un borrador se
// regenera (ej. se corrige un dato antes de que las partes firmen), el contenido anterior
// se conserva aquí en vez de perderse, sin violar la inmutabilidad que aplica una vez sellado.
@Entity
@Table(name = "documento_versiones")
public class DocumentoVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "documento_id", nullable = false)
    private DocumentoFirma documento;

    @Column(name = "contenido_pdf", nullable = false)
    private byte[] contenidoPdf;

    @Column(name = "hash_contenido", nullable = false)
    private String hashContenido;

    @Column(nullable = false)
    private String motivo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creado_por_staff_id")
    private UsuarioInterno creadoPor;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    protected DocumentoVersion() {
    }

    public DocumentoVersion(DocumentoFirma documento, byte[] contenidoPdf, String hashContenido, String motivo, UsuarioInterno creadoPor) {
        this.documento = documento;
        this.contenidoPdf = contenidoPdf;
        this.hashContenido = hashContenido;
        this.motivo = motivo;
        this.creadoPor = creadoPor;
        this.creadoEn = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public DocumentoFirma getDocumento() {
        return documento;
    }

    public byte[] getContenidoPdf() {
        return contenidoPdf;
    }

    public String getHashContenido() {
        return hashContenido;
    }

    public String getMotivo() {
        return motivo;
    }

    public UsuarioInterno getCreadoPor() {
        return creadoPor;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }
}
