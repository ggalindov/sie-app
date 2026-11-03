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
import sie.siejuridicos.caso.Caso;

import java.time.LocalDateTime;

// Ver migracion V76: boletin que el ciclo no pudo completar y que se reintenta hasta lograrlo.
@Entity
@Table(name = "boletines_pendientes")
public class BoletinPendiente {

    public enum Tipo { DESCARGA, BUSQUEDA }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_caso", nullable = false)
    private Caso caso;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_actuacion")
    private ActuacionJudicialRegistrada actuacion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Tipo tipo;

    @Column(name = "url_origen")
    private String urlOrigen;

    @Column(name = "nombre_archivo")
    private String nombreArchivo;

    @Column(name = "coincide_nombre", nullable = false)
    private boolean coincideNombre;

    @Column(nullable = false)
    private int intentos;

    @Column(nullable = false)
    private boolean agotado;

    @Column(name = "ultimo_error")
    private String ultimoError;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion = LocalDateTime.now();

    @Column(name = "fecha_ultimo_intento")
    private LocalDateTime fechaUltimoIntento;

    public Long getId() { return id; }
    public Caso getCaso() { return caso; }
    public void setCaso(Caso caso) { this.caso = caso; }
    public ActuacionJudicialRegistrada getActuacion() { return actuacion; }
    public void setActuacion(ActuacionJudicialRegistrada actuacion) { this.actuacion = actuacion; }
    public Tipo getTipo() { return tipo; }
    public void setTipo(Tipo tipo) { this.tipo = tipo; }
    public String getUrlOrigen() { return urlOrigen; }
    public void setUrlOrigen(String urlOrigen) { this.urlOrigen = urlOrigen; }
    public String getNombreArchivo() { return nombreArchivo; }
    public void setNombreArchivo(String nombreArchivo) { this.nombreArchivo = nombreArchivo; }
    public boolean isCoincideNombre() { return coincideNombre; }
    public void setCoincideNombre(boolean coincideNombre) { this.coincideNombre = coincideNombre; }
    public int getIntentos() { return intentos; }
    public void setIntentos(int intentos) { this.intentos = intentos; }
    public boolean isAgotado() { return agotado; }
    public void setAgotado(boolean agotado) { this.agotado = agotado; }
    public String getUltimoError() { return ultimoError; }
    public void setUltimoError(String ultimoError) { this.ultimoError = ultimoError; }
    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public LocalDateTime getFechaUltimoIntento() { return fechaUltimoIntento; }
    public void setFechaUltimoIntento(LocalDateTime fechaUltimoIntento) { this.fechaUltimoIntento = fechaUltimoIntento; }
}
