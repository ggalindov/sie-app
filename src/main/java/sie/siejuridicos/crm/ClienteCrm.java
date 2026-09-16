package sie.siejuridicos.crm;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import sie.siejuridicos.common.cifrado.CampoCifradoConverter;

import java.time.LocalDateTime;

@Entity
@Table(name = "crm_clientes")
public class ClienteCrm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoClienteCrm tipo = TipoClienteCrm.PERSONA_NATURAL;

    @Convert(converter = CampoCifradoConverter.class)
    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Convert(converter = CampoCifradoConverter.class)
    @Column(name = "cedula_nit")
    private String cedulaNit;

    @Column(name = "cedula_nit_hash", length = 64)
    private String cedulaNitHash;

    @Convert(converter = CampoCifradoConverter.class)
    @Column(name = "correo")
    private String correo;

    @Column(name = "correo_hash", length = 64)
    private String correoHash;

    @Convert(converter = CampoCifradoConverter.class)
    @Column(name = "telefono")
    private String telefono;

    @Column(name = "telefono_hash", length = 64)
    private String telefonoHash;

    @Column(name = "direccion")
    private String direccion;

    @Column(name = "ciudad", length = 100)
    private String ciudad;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 30)
    private EstadoClienteCrm estado = EstadoClienteCrm.ACTIVO;

    @Column(name = "etiqueta", length = 50)
    private String etiqueta;

    @Column(name = "notas")
    private String notas;

    @CreationTimestamp
    @Column(name = "fecha_creacion", updatable = false)
    private LocalDateTime fechaCreacion;

    @UpdateTimestamp
    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;

    public Long getId() {
        return id;
    }

    public TipoClienteCrm getTipo() {
        return tipo;
    }

    public void setTipo(TipoClienteCrm tipo) {
        this.tipo = tipo;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getCedulaNit() {
        return cedulaNit;
    }

    public void setCedulaNit(String cedulaNit) {
        this.cedulaNit = cedulaNit;
    }

    public String getCedulaNitHash() {
        return cedulaNitHash;
    }

    public void setCedulaNitHash(String cedulaNitHash) {
        this.cedulaNitHash = cedulaNitHash;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public String getCorreoHash() {
        return correoHash;
    }

    public void setCorreoHash(String correoHash) {
        this.correoHash = correoHash;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getTelefonoHash() {
        return telefonoHash;
    }

    public void setTelefonoHash(String telefonoHash) {
        this.telefonoHash = telefonoHash;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }

    public String getCiudad() {
        return ciudad;
    }

    public void setCiudad(String ciudad) {
        this.ciudad = ciudad;
    }

    public EstadoClienteCrm getEstado() {
        return estado;
    }

    public void setEstado(EstadoClienteCrm estado) {
        this.estado = estado;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public void setEtiqueta(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String getNotas() {
        return notas;
    }

    public void setNotas(String notas) {
        this.notas = notas;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public LocalDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }
}
