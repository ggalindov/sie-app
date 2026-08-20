package sie.siejuridicos.boletin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sie.siejuridicos.boletin.dto.BoletinEnviadoResponse;

import java.util.List;

@Service
public class BoletinService {

    private final BoletinEnviadoRepository boletinEnviadoRepository;

    public BoletinService(BoletinEnviadoRepository boletinEnviadoRepository) {
        this.boletinEnviadoRepository = boletinEnviadoRepository;
    }

    @Transactional(readOnly = true)
    public List<BoletinEnviadoResponse> listar() {
        return boletinEnviadoRepository.findAllByOrderByFechaEnvioDesc().stream()
                .map(BoletinEnviadoResponse::desde)
                .toList();
    }
}
