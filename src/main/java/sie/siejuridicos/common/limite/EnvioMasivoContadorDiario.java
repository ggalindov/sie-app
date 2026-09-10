package sie.siejuridicos.common.limite;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

// Mapea envios_masivos_contador_diario (ver LimiteEnvioMasivoService y la migración V37) --
// una fila por día, con la cantidad de envíos masivos (correo + WhatsApp) ya reservados hoy.
// Esta entidad no se persiste directo desde Java: toda escritura pasa por la función SQL
// fn_reservar_cupo_envio_masivo (ver EnvioMasivoContadorDiarioRepository), que es la única
// forma de reservar un cupo sin condición de carrera. Solo existe como entidad para que JPA
// pueda mapear el repositorio.
@Entity
@Table(name = "envios_masivos_contador_diario")
public class EnvioMasivoContadorDiario {

    @Id
    private LocalDate fecha;

    @Column(nullable = false)
    private Integer cantidad;

    public LocalDate getFecha() {
        return fecha;
    }

    public Integer getCantidad() {
        return cantidad;
    }
}
