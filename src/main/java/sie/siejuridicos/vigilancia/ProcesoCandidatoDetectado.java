package sie.siejuridicos.vigilancia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import sie.siejuridicos.caso.Cliente;

import java.time.LocalDate;
import java.time.LocalDateTime;

// Pedido explícito del usuario: "implementa todas las mejoras identificadas" (comparando
// contra Lupa Jurídica) -- un proceso que el CPNU devolvió al buscar por el NOMBRE de un
// cliente nuestro (ver RamaJudicialCpnuClient.buscarPorNombre), que todavía NO está en
// nuestra tabla de casos. Nunca se convierte en Caso automáticamente: la búsqueda por nombre
// es ambigua (puede ser un tocayo real, no necesariamente nuestro cliente) -- esta fila es
// solo un candidato para que un abogado/admin lo revise en /admin/procesos-detectados y
// decida si de verdad es del cliente (y si lo es, lo crea a mano como caso real).
@Entity
@Table(name = "procesos_candidatos_detectados")
public class ProcesoCandidatoDetectado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_cliente", nullable = false)
    private Cliente cliente;

    @Column(name = "llave_proceso", nullable = false)
    private String llaveProceso;

    @Column(name = "despacho")
    private String despacho;

    @Column(name = "departamento")
    private String departamento;

    @Column(name = "sujetos_procesales", columnDefinition = "TEXT")
    private String sujetosProcesales;

    @Column(name = "fecha_proceso")
    private LocalDate fechaProceso;

    @Column(name = "fecha_ultima_actuacion")
    private LocalDate fechaUltimaActuacion;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false)
    private EstadoProcesoCandidato estado = EstadoProcesoCandidato.PENDIENTE;

    @CreationTimestamp
    @Column(name = "fecha_deteccion", updatable = false)
    private LocalDateTime fechaDeteccion;

    @Column(name = "fecha_revision")
    private LocalDateTime fechaRevision;

    public static ProcesoCandidatoDetectado desde(Cliente cliente, RamaJudicialCpnuClient.ProcesoCandidato c) {
        ProcesoCandidatoDetectado entidad = new ProcesoCandidatoDetectado();
        entidad.cliente = cliente;
        entidad.llaveProceso = c.llaveProceso();
        entidad.despacho = c.despacho();
        entidad.departamento = c.departamento();
        entidad.sujetosProcesales = c.sujetosProcesales();
        entidad.fechaProceso = c.fechaProceso();
        entidad.fechaUltimaActuacion = c.fechaUltimaActuacion();
        return entidad;
    }

    public Long getId() {
        return id;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public String getLlaveProceso() {
        return llaveProceso;
    }

    public String getDespacho() {
        return despacho;
    }

    public String getDepartamento() {
        return departamento;
    }

    public String getSujetosProcesales() {
        return sujetosProcesales;
    }

    public LocalDate getFechaProceso() {
        return fechaProceso;
    }

    public LocalDate getFechaUltimaActuacion() {
        return fechaUltimaActuacion;
    }

    public EstadoProcesoCandidato getEstado() {
        return estado;
    }

    public void setEstado(EstadoProcesoCandidato estado) {
        this.estado = estado;
        this.fechaRevision = LocalDateTime.now();
    }

    public LocalDateTime getFechaDeteccion() {
        return fechaDeteccion;
    }

    public LocalDateTime getFechaRevision() {
        return fechaRevision;
    }
}
