package sie.siejuridicos.vigilancia;

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
import sie.siejuridicos.caso.Caso;
import sie.siejuridicos.tarea.Tarea;

import java.time.LocalDate;
import java.time.LocalDateTime;

// Gap real detectado en la auditoria del plan de desarrollo (ver migracion V60): antes el
// termino procesal (ver InferenciaTerminoProcesal) solo se calculaba AL VUELO para mostrar
// una insignia dentro del modal de Seguimiento -- si el abogado no entraba a revisarlo, no
// habia ninguna alerta activa en otro lado del panel. Esta entidad persiste cada termino
// DETECTADO (una sola vez, ver TerminoProcesalRepository.existsByActuacionId) y lo liga a la
// tarea real que le crea al abogado asignado del caso (ver TerminoProcesalService), para que
// aparezca tanto en /admin/tareas como con semaforo en /admin/calendario.
//
// "estado" (CORRIENDO/POR_VENCER/VENCIDO) NO vive aqui -- depende de la fecha de HOY, se
// recalcula siempre al leer (ver InferenciaTerminoProcesal.calcularEstado), nunca se
// persiste un valor que quedaria desactualizado al dia siguiente.
@Entity
@Table(name = "terminos_procesales")
public class TerminoProcesal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_caso", nullable = false)
    private Caso caso;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_actuacion", nullable = false)
    private ActuacionJudicialRegistrada actuacion;

    // Null si no se pudo crear la tarea (el caso no tenia abogado asignado en ese momento,
    // ver TerminoProcesalService) -- el termino igual queda registrado.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_tarea")
    private Tarea tarea;

    @Column(name = "descripcion", nullable = false)
    private String descripcion;

    @Column(name = "dias_habiles", nullable = false)
    private int diasHabiles;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

    @Column(name = "es_oficial", nullable = false)
    private boolean esOficial;

    @CreationTimestamp
    @Column(name = "fecha_creacion", updatable = false)
    private LocalDateTime fechaCreacion;

    public Long getId() {
        return id;
    }

    public Caso getCaso() {
        return caso;
    }

    public void setCaso(Caso caso) {
        this.caso = caso;
    }

    public ActuacionJudicialRegistrada getActuacion() {
        return actuacion;
    }

    public void setActuacion(ActuacionJudicialRegistrada actuacion) {
        this.actuacion = actuacion;
    }

    public Tarea getTarea() {
        return tarea;
    }

    public void setTarea(Tarea tarea) {
        this.tarea = tarea;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public int getDiasHabiles() {
        return diasHabiles;
    }

    public void setDiasHabiles(int diasHabiles) {
        this.diasHabiles = diasHabiles;
    }

    public LocalDate getFechaVencimiento() {
        return fechaVencimiento;
    }

    public void setFechaVencimiento(LocalDate fechaVencimiento) {
        this.fechaVencimiento = fechaVencimiento;
    }

    public boolean isEsOficial() {
        return esOficial;
    }

    public void setEsOficial(boolean esOficial) {
        this.esOficial = esOficial;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }
}
