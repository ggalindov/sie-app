package sie.siejuridicos.usuario;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "usuarios_internos")
public class UsuarioInterno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false, unique = true)
    private String correo;

    @Column(nullable = false)
    private String contrasena;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RolUsuario rol;

    @CreationTimestamp
    @Column(name = "fecha_creacion", updatable = false)
    private LocalDateTime fechaCreacion;

    @Column(nullable = false)
    private boolean activo = true;

    // Firma manuscrita propia, subida una sola vez desde "Mi firma" en el perfil y
    // reutilizada por el motor de firma electrónica para auto-firmar cada documento donde
    // este usuario queda como firmante de staff (gerente o abogado) -- ver
    // sie.siejuridicos.documento.DocumentoAutoFirmanteStaff. Relación 1:1 opcional, por eso
    // son columnas sueltas aquí y no una tabla aparte (ver migración V48).
    @Column(name = "firma_png")
    private byte[] firmaPng;

    @Column(name = "firma_content_type")
    private String firmaContentType;

    @Column(name = "firma_actualizada_en")
    private java.time.LocalDateTime firmaActualizadaEn;

    public byte[] getFirmaPng() {
        return firmaPng;
    }

    public String getFirmaContentType() {
        return firmaContentType;
    }

    public java.time.LocalDateTime getFirmaActualizadaEn() {
        return firmaActualizadaEn;
    }

    // Pedido explícito del usuario: "el sistema debe insertar firma del gerente... el
    // sistema ingresa la firma de los abogados" -- cada usuario sube la suya una sola vez
    // (o la reemplaza) desde su propio perfil; nunca la sube otro en su nombre.
    public void actualizarFirma(byte[] firmaPng, String contentType) {
        this.firmaPng = firmaPng;
        this.firmaContentType = contentType;
        this.firmaActualizadaEn = java.time.LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public String getContrasena() {
        return contrasena;
    }

    public void setContrasena(String contrasena) {
        this.contrasena = contrasena;
    }

    public RolUsuario getRol() {
        return rol;
    }

    public void setRol(RolUsuario rol) {
        this.rol = rol;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }
}
