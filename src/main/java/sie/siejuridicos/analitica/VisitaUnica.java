package sie.siejuridicos.analitica;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

// Sin uso de operaciones JPA normales (save/find): el registro y el conteo se hacen a
// través de fn_registrar_visita_unica/fn_contar_visitantes_mes_actual (ver V20), atómicos
// en la base de datos. Esta entidad existe solo para que VisitaUnicaRepository pueda
// extender JpaRepository (Spring Data JPA no admite un repositorio "sin entidad" — ver
// el mismo comentario en ConversacionChatbotRepository).
@Entity
@Table(name = "visitas_unicas_mensuales")
public class VisitaUnica {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "anio_mes", nullable = false, length = 7)
    private String anioMes;

    @Column(name = "visitante_id", nullable = false, length = 64)
    private String visitanteId;

    @Column(name = "primera_vez", nullable = false, updatable = false)
    private LocalDateTime primeraVez;

    public Long getId() {
        return id;
    }

    public String getAnioMes() {
        return anioMes;
    }

    public String getVisitanteId() {
        return visitanteId;
    }

    public LocalDateTime getPrimeraVez() {
        return primeraVez;
    }
}
