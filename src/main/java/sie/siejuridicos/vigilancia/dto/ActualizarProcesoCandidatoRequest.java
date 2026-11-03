package sie.siejuridicos.vigilancia.dto;

import sie.siejuridicos.vigilancia.EstadoProcesoCandidato;

// Pedido explícito del usuario: "implementa todas las mejoras identificadas" -- el admin
// revisa un candidato detectado y decide: DESCARTADO (no es del cliente, un tocayo real) o
// YA_ES_CASO (lo verificó y ya creó el caso real a mano con el radicado de este candidato).
// Nunca PENDIENTE desde aquí -- volver a pendiente no tiene sentido una vez revisado.
public record ActualizarProcesoCandidatoRequest(EstadoProcesoCandidato estado) {
}
