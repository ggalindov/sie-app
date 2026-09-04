package sie.siejuridicos.caso;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import sie.siejuridicos.common.cifrado.CampoCifradoConverter;

import java.time.LocalDateTime;

// nombre/correo/telefono se guardan cifrados (AES-256-GCM, ver CampoCifradoConverter): son
// datos de identificación de una persona real ligados a un caso legal. El cifrado/descifrado
// es transparente para el resto del código Java (Hibernate lo aplica en el límite con la
// base de datos), así que en memoria estos campos siempre se ven como texto plano normal.
//
// correoHash es aparte: un índice ciego (HMAC, no reversible) que sí se puede comparar por
// igualdad en SQL -- correo en sí queda cifrado con IV aleatorio (dos cifrados del mismo
// correo dan bytes distintos), así que "WHERE correo = ?" ya no funcionaría directamente
// sobre esa columna. La unicidad ahora vive en correoHash, no en correo.
//
// correo/correoHash son OPCIONALES (ver migración V28): un cliente puede representar un
// sujeto procesal cuyo correo todavía no está capturado en la hoja de la firma -- el caso se
// sincroniza igual (pedido explícito: "no se me puede perder ni faltar ninguno"), solo que
// sin poder enviarle notificación hasta que la hoja se actualice con su contacto. Sin correo
// no hay forma de deduplicar/reidentificar al cliente entre sincronizaciones (no hay nada
// determinista que comparar), así que cada fila sin correo obtiene su propio Cliente.
@Entity
@Table(name = "clientes")
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Convert(converter = CampoCifradoConverter.class)
    @Column(nullable = false)
    private String nombre;

    @Convert(converter = CampoCifradoConverter.class)
    private String correo;

    @Column(name = "correo_hash", unique = true)
    private String correoHash;

    @Convert(converter = CampoCifradoConverter.class)
    private String telefono;

    @CreationTimestamp
    @Column(name = "fecha_creacion", updatable = false)
    private LocalDateTime fechaCreacion;

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

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }
}
