package sie.siejuridicos.vigilancia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Fila única (id=1, ver migración V73) con la posición del cursor rotativo de
// SeguimientoJudicialService -- mismo patrón que VigilanciaCursorBoletines (ver su
// comentario): persistido para que sobreviva un reinicio del backend.
@Entity
@Table(name = "vigilancia_cursor_seguimiento")
public class VigilanciaCursorSeguimiento {

    @Id
    private Short id;

    @Column(nullable = false)
    private int posicion;

    public Short getId() {
        return id;
    }

    public int getPosicion() {
        return posicion;
    }

    public void setPosicion(int posicion) {
        this.posicion = posicion;
    }
}
