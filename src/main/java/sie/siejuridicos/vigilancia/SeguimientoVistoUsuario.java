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
import sie.siejuridicos.caso.Caso;
import sie.siejuridicos.usuario.UsuarioInterno;

import java.time.LocalDateTime;

// Marca, POR USUARIO interno (no global), cuándo vio por última vez el seguimiento judicial
// de un caso puntual -- pedido explícito del usuario: "si es nuevo para cada persona, si no
// la ha abierto le sale un botoncito rojo". Dos abogados distintos pueden tener el mismo caso
// con un estado de lectura independiente (ver migración V59), así que esto NO vive como un
// simple campo en Caso.
//
// Se actualiza (upsert) cada vez que VigilanciaAdminController.lineaTiempo() responde para
// ese usuario -- abrir el modal de seguimiento de un caso ES la acción de "revisarlo".
@Entity
@Table(name = "seguimiento_visto_usuario")
public class SeguimientoVistoUsuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_caso", nullable = false)
    private Caso caso;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_usuario", nullable = false)
    private UsuarioInterno usuario;

    @Column(name = "fecha_vista", nullable = false)
    private LocalDateTime fechaVista;

    public Long getId() {
        return id;
    }

    public Caso getCaso() {
        return caso;
    }

    public void setCaso(Caso caso) {
        this.caso = caso;
    }

    public UsuarioInterno getUsuario() {
        return usuario;
    }

    public void setUsuario(UsuarioInterno usuario) {
        this.usuario = usuario;
    }

    public LocalDateTime getFechaVista() {
        return fechaVista;
    }

    public void setFechaVista(LocalDateTime fechaVista) {
        this.fechaVista = fechaVista;
    }
}
