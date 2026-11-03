package sie.siejuridicos.vigilancia;

import org.springframework.stereotype.Service;
import sie.siejuridicos.vigilancia.dto.DocumentoRecienteResponse;

import java.util.List;
import java.util.Optional;

// Orquesta RamaJudicialCpnuClient + PublicacionesProcesalesClient para responder "¿cuál es
// la última decisión de fondo de este caso, y qué documento del boletín oficial podría
// corresponderle?" -- SOLO LECTURA, nunca envía nada a ningún cliente (ver
// CasoAdminController.documentoReciente()). El envío real todavía no existe en el sistema a
// propósito, ver memoria del proyecto (vigilancia-judicial-alcance.md): falta que un humano
// decida el diseño final antes de automatizarlo.
@Service
public class VigilanciaJudicialService {

    private final RamaJudicialCpnuClient cpnuClient;
    private final PublicacionesProcesalesClient publicacionesProcesalesClient;

    public VigilanciaJudicialService(RamaJudicialCpnuClient cpnuClient,
                                      PublicacionesProcesalesClient publicacionesProcesalesClient) {
        this.cpnuClient = cpnuClient;
        this.publicacionesProcesalesClient = publicacionesProcesalesClient;
    }

    public DocumentoRecienteResponse buscarUltimoDocumentoFundamental(String radicadoId) {
        Optional<Long> idProceso = cpnuClient.buscarIdProceso(radicadoId);
        if (idProceso.isEmpty()) {
            return DocumentoRecienteResponse.noDisponible(
                    "No encontramos ese radicado en la Rama Judicial (CPNU). Puede ser muy antiguo, "
                            + "estar inactivo, o venir de una fuente que no usa radicado CPNU de 23 dígitos "
                            + "(ver FuenteCaso).");
        }

        // La Rama Judicial ya las devuelve de la más reciente a la más antigua (confirmado en
        // el spike real, ver RamaJudicialCpnuClient) -- la primera que sea "fundamental" es la
        // más reciente de fondo, sin necesidad de ordenar nada aquí.
        List<ActuacionJudicial> actuaciones = cpnuClient.listarActuaciones(idProceso.get());
        Optional<ActuacionJudicial> ultimaFundamental = actuaciones.stream()
                .filter(ClasificadorActuacionFundamental::esFundamental)
                .findFirst();
        if (ultimaFundamental.isEmpty()) {
            return DocumentoRecienteResponse.sinActuacionFundamental();
        }

        ActuacionJudicial actuacion = ultimaFundamental.get();
        List<DocumentoProvidenciaCandidato> candidatos = publicacionesProcesalesClient.buscarDocumentos(
                radicadoId,
                actuacion.fechaRegistro() != null ? actuacion.fechaRegistro().toLocalDate() : null,
                actuacion.nombreActuacion());
        return DocumentoRecienteResponse.encontrada(actuacion, candidatos);
    }
}
