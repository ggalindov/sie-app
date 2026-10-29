package sie.siejuridicos.documento;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

// Sello de tiempo RFC 3161 agregado de un día completo de checkpoints (ver
// AnclaDiariaIntegridadScheduler y V54__documento_checkpoints_anclas_diarias.sql):
// 'tokenRfc3161' nace null (la TSA externa puede no responder a tiempo) y se completa en un
// reintento posterior, a diferencia de documento_eventos/documento_eventos_checkpoints esta
// tabla sí permite ese único UPDATE legítimo.
@Entity
@Table(name = "anclas_diarias_integridad")
public class AnclaDiariaIntegridad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "hash_agregado", nullable = false)
    private String hashAgregado;

    @Column(name = "token_rfc3161")
    private byte[] tokenRfc3161;

    @Column(name = "url_tsa")
    private String urlTsa;

    @Column(name = "cantidad_documentos", nullable = false)
    private int cantidadDocumentos;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    protected AnclaDiariaIntegridad() {
    }

    public AnclaDiariaIntegridad(LocalDate fecha, String hashAgregado, int cantidadDocumentos, LocalDateTime creadoEn) {
        this.fecha = fecha;
        this.hashAgregado = hashAgregado;
        this.cantidadDocumentos = cantidadDocumentos;
        this.creadoEn = creadoEn;
    }

    public void completarSello(byte[] tokenRfc3161, String urlTsa) {
        this.tokenRfc3161 = tokenRfc3161;
        this.urlTsa = urlTsa;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public String getHashAgregado() {
        return hashAgregado;
    }

    public byte[] getTokenRfc3161() {
        return tokenRfc3161;
    }

    public String getUrlTsa() {
        return urlTsa;
    }

    public int getCantidadDocumentos() {
        return cantidadDocumentos;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }
}
