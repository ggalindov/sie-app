package sie.siejuridicos.documento;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface DocumentoOtpRepository extends JpaRepository<DocumentoOtp, UUID> {

    Optional<DocumentoOtp> findFirstByFirmanteIdOrderByCreadoEnDesc(UUID firmanteId);

    // Códigos emitidos a un firmante desde un instante dado: tope diario de OtpService#solicitar.
    long countByFirmanteIdAndCreadoEnAfter(UUID firmanteId, LocalDateTime desde);
}
