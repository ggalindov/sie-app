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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

// "Fotografía" diaria de cuántos eventos tenía un documento en ese momento (ver
// V54__documento_checkpoints_anclas_diarias.sql): como esta tabla y documento_eventos son
// append-only, un checkpoint pasado con MÁS eventos de los que existen hoy es evidencia
// concluyente de que alguien borró eventos del final de la cadena -- algo que
// DocumentoEventoChainVerifier no puede detectar por sí solo (ver AnclaDiariaIntegridadScheduler,
// único escritor de esta tabla).
@Entity
@Table(name = "documento_eventos_checkpoints")
public class DocumentoEventoCheckpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "documento_id", nullable = false)
    private DocumentoFirma documento;

    @Column(name = "cantidad_eventos", nullable = false)
    private long cantidadEventos;

    @Column(name = "hash_ultimo_evento", nullable = false)
    private String hashUltimoEvento;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    protected DocumentoEventoCheckpoint() {
    }

    public DocumentoEventoCheckpoint(DocumentoFirma documento, long cantidadEventos, String hashUltimoEvento,
                                      LocalDate fecha, LocalDateTime creadoEn) {
        this.documento = documento;
        this.cantidadEventos = cantidadEventos;
        this.hashUltimoEvento = hashUltimoEvento;
        this.fecha = fecha;
        this.creadoEn = creadoEn;
    }

    public UUID getId() {
        return id;
    }

    public DocumentoFirma getDocumento() {
        return documento;
    }

    public long getCantidadEventos() {
        return cantidadEventos;
    }

    public String getHashUltimoEvento() {
        return hashUltimoEvento;
    }

    public LocalDate getFecha() {
        return fecha;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }
}
