package sie.siejuridicos.croma;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

// Entidad para el control estricto de consumo de créditos de Croma.
// El plan gratuito asigna 1,500 créditos mensuales (150 consultas en vivo de 10 créditos c/u).
// Esta tabla persiste el consumo mensual por período YYYY-MM para evitar exceder el límite.
@Entity
@Table(name = "croma_creditos_mensual")
public class CromaCreditosMensual {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mes_ano", nullable = false, unique = true, length = 7)
    private String mesAno;

    @Column(name = "creditos_consumidos", nullable = false)
    private int creditosConsumidos;

    @Column(name = "consultas_realizadas", nullable = false)
    private int consultasRealizadas;

    @Column(name = "limite_creditos", nullable = false)
    private int limiteCreditos;

    @Column(name = "fecha_actualizacion", nullable = false)
    private OffsetDateTime fechaActualizacion;

    public CromaCreditosMensual() {
    }

    public CromaCreditosMensual(String mesAno, int limiteCreditos) {
        this.mesAno = mesAno;
        this.creditosConsumidos = 0;
        this.consultasRealizadas = 0;
        this.limiteCreditos = limiteCreditos;
        this.fechaActualizacion = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getMesAno() {
        return mesAno;
    }

    public void setMesAno(String mesAno) {
        this.mesAno = mesAno;
    }

    public int getCreditosConsumidos() {
        return creditosConsumidos;
    }

    public void setCreditosConsumidos(int creditosConsumidos) {
        this.creditosConsumidos = creditosConsumidos;
    }

    public int getConsultasRealizadas() {
        return consultasRealizadas;
    }

    public void setConsultasRealizadas(int consultasRealizadas) {
        this.consultasRealizadas = consultasRealizadas;
    }

    public int getLimiteCreditos() {
        return limiteCreditos;
    }

    public void setLimiteCreditos(int limiteCreditos) {
        this.limiteCreditos = limiteCreditos;
    }

    public OffsetDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    public void setFechaActualizacion(OffsetDateTime fechaActualizacion) {
        this.fechaActualizacion = fechaActualizacion;
    }
}
