package sie.siejuridicos.vigilancia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

// Una medicion de la sonda de salud (ver V77 y PortalSaludSonda).
@Entity
@Table(name = "portal_salud")
public class PortalSalud {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private OffsetDateTime fecha = OffsetDateTime.now();

    @Column(nullable = false, length = 20)
    private String servicio;

    @Column(nullable = false)
    private boolean ok;

    private Integer http;

    @Column(name = "latencia_ms")
    private Integer latenciaMs;

    private String error;

    public PortalSalud() {
    }

    public PortalSalud(String servicio, boolean ok, Integer http, Integer latenciaMs, String error) {
        this.servicio = servicio;
        this.ok = ok;
        this.http = http;
        this.latenciaMs = latenciaMs;
        this.error = error;
    }

    public Long getId() { return id; }
    public OffsetDateTime getFecha() { return fecha; }
    public String getServicio() { return servicio; }
    public boolean isOk() { return ok; }
    public Integer getHttp() { return http; }
    public Integer getLatenciaMs() { return latenciaMs; }
    public String getError() { return error; }
}
