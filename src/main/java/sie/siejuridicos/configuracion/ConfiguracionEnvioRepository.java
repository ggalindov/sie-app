package sie.siejuridicos.configuracion;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConfiguracionEnvioRepository extends JpaRepository<ConfiguracionEnvio, ClaveConfiguracionEnvio> {
}
