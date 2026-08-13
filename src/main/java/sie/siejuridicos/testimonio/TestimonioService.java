package sie.siejuridicos.testimonio;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sie.siejuridicos.common.exception.EntidadInvalidaException;
import sie.siejuridicos.common.exception.RecursoNoEncontradoException;
import sie.siejuridicos.testimonio.dto.CrearTestimonioRequest;
import sie.siejuridicos.testimonio.dto.TestimonioAdminResponse;
import sie.siejuridicos.testimonio.dto.TestimonioResponse;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TestimonioService {

    private final TestimonioPublicoRepository testimonioRepository;

    public TestimonioService(TestimonioPublicoRepository testimonioRepository) {
        this.testimonioRepository = testimonioRepository;
    }

    @Transactional
    public TestimonioResponse crear(CrearTestimonioRequest request) {
        TestimonioPublico testimonio = new TestimonioPublico();
        testimonio.setNombre(request.nombre());
        testimonio.setEmpresa(request.empresa());
        testimonio.setCargo(request.cargo());
        testimonio.setCita(request.cita());
        testimonio.setCalificacion(request.calificacion());
        testimonio.setCorreo(request.correo());
        testimonio.setEstado(EstadoTestimonio.PENDIENTE);
        return TestimonioResponse.desde(testimonioRepository.save(testimonio));
    }

    @Transactional(readOnly = true)
    public List<TestimonioResponse> listarAprobados() {
        return testimonioRepository.findByEstadoOrderByFechaCreacionDesc(EstadoTestimonio.APROBADO).stream()
                .map(TestimonioResponse::desde)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TestimonioAdminResponse> listarTodos() {
        return testimonioRepository.findAllByOrderByFechaCreacionDesc().stream()
                .map(TestimonioAdminResponse::desde)
                .toList();
    }

    @Transactional
    public TestimonioAdminResponse moderar(Long id, EstadoTestimonio nuevoEstado) {
        if (nuevoEstado == EstadoTestimonio.PENDIENTE) {
            throw new EntidadInvalidaException("No se puede devolver un testimonio al estado pendiente");
        }
        TestimonioPublico testimonio = testimonioRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el testimonio con id " + id));
        testimonio.setEstado(nuevoEstado);
        testimonio.setFechaModeracion(LocalDateTime.now());
        return TestimonioAdminResponse.desde(testimonio);
    }
}
