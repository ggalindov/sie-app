package sie.siejuridicos.caso;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import sie.siejuridicos.usuario.UsuarioInterno;

import java.time.LocalDateTime;

// Pedido explícito del usuario (2026-11-09): "un pop-up al actualizarlos que detecte que
// casos tuvieron cambios y cuales fueron, esto para llevar una mayor integridad entre el
// google sheet y el sistema". Una fila por cada campo que cambió en un caso YA existente
// durante CasoService.sincronizarDesdeHoja() -- por ahora cubre los 3 campos con un
// antes/después ya disponible directamente en ese método sin refactorizar su lógica interna:
// radicado, nombre_en_hoja y abogado asignado (los de mayor impacto operativo: un radicado o
// abogado equivocado desvía notificaciones y vigilancia al caso o persona incorrecta).
@Entity
@Table(name = "caso_cambios_detectados")
public class CasoCambioDetectado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_caso", nullable = false)
    private Caso caso;

    @Column(nullable = false)
    private String campo;

    @Column(name = "valor_anterior", columnDefinition = "TEXT")
    private String valorAnterior;

    @Column(name = "valor_nuevo", columnDefinition = "TEXT")
    private String valorNuevo;

    @CreationTimestamp
    @Column(name = "fecha_deteccion", updatable = false)
    private LocalDateTime fechaDeteccion;

    @Column(nullable = false)
    private boolean revisado = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "revisado_por_id")
    private UsuarioInterno revisadoPor;

    @Column(name = "fecha_revisado")
    private LocalDateTime fechaRevisado;

    public static CasoCambioDetectado de(Caso caso, String campo, String valorAnterior, String valorNuevo) {
        CasoCambioDetectado cambio = new CasoCambioDetectado();
        cambio.caso = caso;
        cambio.campo = campo;
        cambio.valorAnterior = valorAnterior;
        cambio.valorNuevo = valorNuevo;
        return cambio;
    }

    public Long getId() {
        return id;
    }

    public Caso getCaso() {
        return caso;
    }

    public String getCampo() {
        return campo;
    }

    public String getValorAnterior() {
        return valorAnterior;
    }

    public String getValorNuevo() {
        return valorNuevo;
    }

    public LocalDateTime getFechaDeteccion() {
        return fechaDeteccion;
    }

    public boolean isRevisado() {
        return revisado;
    }

    public void setRevisado(boolean revisado) {
        this.revisado = revisado;
    }

    public UsuarioInterno getRevisadoPor() {
        return revisadoPor;
    }

    public void setRevisadoPor(UsuarioInterno revisadoPor) {
        this.revisadoPor = revisadoPor;
    }

    public LocalDateTime getFechaRevisado() {
        return fechaRevisado;
    }

    public void setFechaRevisado(LocalDateTime fechaRevisado) {
        this.fechaRevisado = fechaRevisado;
    }
}
