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

import java.time.LocalDate;
import java.time.LocalDateTime;

// Una actuación de la Rama Judicial que YA guardamos en nuestro propio historial (ver
// SeguimientoJudicialService), distinta de ActuacionJudicial (ese es el DTO de la respuesta
// EN VIVO de RamaJudicialCpnuClient, nunca persistido tal cual). Guardar un historial propio
// es lo que permite detectar qué actuación es NUEVA desde la última corrida
// (fechaDetectada), en vez de limitarse a mostrar el estado actual como hace
// CasoService.consultar().
//
// Nunca se actualiza una fila existente: una actuación real de un juzgado no cambia de
// contenido una vez publicada (ver uqCasoExterna en la migración V56) -- si la Rama Judicial
// llegara a corregir el texto de una actuación ya guardada, eso se vería reflejado en una
// NUEVA actuación (una corrección/aclaración), no editando esta fila.
@Entity
@Table(name = "actuaciones_judiciales")
public class ActuacionJudicialRegistrada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_caso", nullable = false)
    private Caso caso;

    @Column(name = "id_actuacion_externa", nullable = false)
    private long idActuacionExterna;

    // LocalDateTime (no LocalDate) a propósito -- pedido explícito del usuario: "fecha y hora
    // exacta". Ver migración V65 y el comentario de ActuacionJudicial.
    @Column(name = "fecha_actuacion")
    private LocalDateTime fechaActuacion;

    @Column(name = "nombre_actuacion", nullable = false)
    private String nombreActuacion;

    @Column(name = "anotacion")
    private String anotacion;

    @Column(name = "fecha_registro")
    private LocalDateTime fechaRegistro;

    @Column(name = "con_documentos", nullable = false)
    private boolean conDocumentos;

    // Consecutivo real que le asigna el despacho (ver migración V58) -- contexto honesto de
    // "qué tan avanzado va el proceso", nunca un conteo propio inventado.
    @Column(name = "consecutivo")
    private Integer consecutivo;

    // Término REAL informado por la propia Rama Judicial para esta actuación (ver migración
    // V58 y ActuacionJudicial) -- null cuando esta actuación no abre ningún término. Cuando
    // está presente, ActuacionRegistradaResponse lo prefiere sobre la inferencia por palabras
    // clave de InferenciaTerminoProcesal.
    @Column(name = "fecha_inicial_termino")
    private LocalDate fechaInicialTermino;

    @Column(name = "fecha_final_termino")
    private LocalDate fechaFinalTermino;

    @Column(name = "es_fundamental", nullable = false)
    private boolean esFundamental;

    @CreationTimestamp
    @Column(name = "fecha_detectada", updatable = false)
    private LocalDateTime fechaDetectada;

    public static ActuacionJudicialRegistrada desde(Caso caso, ActuacionJudicial actuacion, boolean esFundamental) {
        ActuacionJudicialRegistrada entidad = new ActuacionJudicialRegistrada();
        entidad.caso = caso;
        entidad.idActuacionExterna = actuacion.idActuacionExterna();
        entidad.fechaActuacion = actuacion.fechaActuacion();
        entidad.nombreActuacion = actuacion.nombreActuacion();
        entidad.anotacion = actuacion.anotacion();
        entidad.fechaRegistro = actuacion.fechaRegistro();
        entidad.conDocumentos = actuacion.conDocumentos();
        entidad.esFundamental = esFundamental;
        entidad.consecutivo = actuacion.consActuacion();
        entidad.fechaInicialTermino = actuacion.fechaInicialTermino();
        entidad.fechaFinalTermino = actuacion.fechaFinalTermino();
        return entidad;
    }

    public Long getId() {
        return id;
    }

    public Caso getCaso() {
        return caso;
    }

    public long getIdActuacionExterna() {
        return idActuacionExterna;
    }

    public LocalDateTime getFechaActuacion() {
        return fechaActuacion;
    }

    public String getNombreActuacion() {
        return nombreActuacion;
    }

    public String getAnotacion() {
        return anotacion;
    }

    public LocalDateTime getFechaRegistro() {
        return fechaRegistro;
    }

    public boolean isConDocumentos() {
        return conDocumentos;
    }

    public boolean isEsFundamental() {
        return esFundamental;
    }

    public Integer getConsecutivo() {
        return consecutivo;
    }

    public void setConsecutivo(Integer consecutivo) {
        this.consecutivo = consecutivo;
    }

    public LocalDate getFechaInicialTermino() {
        return fechaInicialTermino;
    }

    public void setFechaInicialTermino(LocalDate fechaInicialTermino) {
        this.fechaInicialTermino = fechaInicialTermino;
    }

    public LocalDate getFechaFinalTermino() {
        return fechaFinalTermino;
    }

    public void setFechaFinalTermino(LocalDate fechaFinalTermino) {
        this.fechaFinalTermino = fechaFinalTermino;
    }

    public LocalDateTime getFechaDetectada() {
        return fechaDetectada;
    }
}
