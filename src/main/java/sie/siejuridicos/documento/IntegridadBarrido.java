package sie.siejuridicos.documento;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

// Una fila por cada corrida de DocumentIntegrityScanner, haya o no encontrado alteraciones --
// ver V52__integridad_barridos.sql para el porqué (prueba auditable de vigilancia continua,
// no solo de los fallos).
@Entity
@Table(name = "integridad_barridos")
public class IntegridadBarrido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime ejecutadoEn;
    private int documentosRevisados;
    private int alteracionesEncontradas;
    private long duracionMs;

    protected IntegridadBarrido() {
    }

    public IntegridadBarrido(LocalDateTime ejecutadoEn, int documentosRevisados, int alteracionesEncontradas, long duracionMs) {
        this.ejecutadoEn = ejecutadoEn;
        this.documentosRevisados = documentosRevisados;
        this.alteracionesEncontradas = alteracionesEncontradas;
        this.duracionMs = duracionMs;
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getEjecutadoEn() {
        return ejecutadoEn;
    }

    public int getDocumentosRevisados() {
        return documentosRevisados;
    }

    public int getAlteracionesEncontradas() {
        return alteracionesEncontradas;
    }

    public long getDuracionMs() {
        return duracionMs;
    }
}
