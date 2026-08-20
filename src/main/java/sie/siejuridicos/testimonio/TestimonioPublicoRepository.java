package sie.siejuridicos.testimonio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestimonioPublicoRepository extends JpaRepository<TestimonioPublico, Long> {

    List<TestimonioPublico> findByEstadoOrderByFechaCreacionDesc(EstadoTestimonio estado);

    List<TestimonioPublico> findAllByOrderByFechaCreacionDesc();

    // usado por EstadisticasService para el resumen del panel administrativo
    long countByEstado(EstadoTestimonio estado);
}
