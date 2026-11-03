package sie.siejuridicos.vigilancia.dto;

import sie.siejuridicos.vigilancia.EstadoProcesoCandidato;
import sie.siejuridicos.vigilancia.ProcesoCandidatoDetectado;

import java.time.LocalDate;
import java.time.LocalDateTime;

// Pedido explícito del usuario: "implementa todas las mejoras identificadas" -- un proceso
// que el CPNU devolvió al buscar por el nombre de un cliente, que todavía no está en
// nuestra tabla de casos (ver DeteccionProcesosNuevosService/ProcesoCandidatoDetectado).
public record ProcesoCandidatoResponse(
        Long id,
        Long clienteId,
        String clienteNombre,
        String llaveProceso,
        String despacho,
        String departamento,
        String sujetosProcesales,
        LocalDate fechaProceso,
        LocalDate fechaUltimaActuacion,
        EstadoProcesoCandidato estado,
        LocalDateTime fechaDeteccion
) {
    public static ProcesoCandidatoResponse desde(ProcesoCandidatoDetectado p) {
        return new ProcesoCandidatoResponse(
                p.getId(),
                p.getCliente().getId(),
                p.getCliente().getNombre(),
                p.getLlaveProceso(),
                p.getDespacho(),
                p.getDepartamento(),
                p.getSujetosProcesales(),
                p.getFechaProceso(),
                p.getFechaUltimaActuacion(),
                p.getEstado(),
                p.getFechaDeteccion());
    }
}
