package sie.siejuridicos.vigilancia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Fila única (id=1, ver migración V71) con la posición del cursor rotativo de
// BoletinJudicialCacheService -- persistido para que sobreviva un reinicio del backend, ver
// el comentario de esa clase sobre por qué un AtomicInteger en memoria no bastaba.
@Entity
@Table(name = "vigilancia_cursor_boletines")
public class VigilanciaCursorBoletines {

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
