package sie.siejuridicos.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import sie.siejuridicos.common.exception.ErrorResponse;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalDateTime;

// Rechaza de entrada cuerpos de solicitud anormalmente grandes (ej. un `mensaje` de 50MB
// en el formulario público) antes de que lleguen a los @Valid/@Size de cada DTO.
//
// Dos capas: (1) rechazo rápido por Content-Length cuando el cliente lo declara con
// sinceridad (cubre el caso normal: fetch/axios del frontend, curl, Postman siempre lo
// envían para JSON); (2) un límite duro a nivel de stream (LimitingRequestWrapper) que
// corta la lectura apenas se supera maxBytes, sin importar lo que diga Content-Length.
// La capa (2) es la que de verdad cierra el hueco: un cliente que use
// "Transfer-Encoding: chunked" sin Content-Length (Content-Length llega en -1, así que la
// capa (1) no lo detecta) antes pasaba sin límite alguno; ahora, aunque no haya
// Content-Length, la lectura se corta igual al llegar a maxBytes.
@Component
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private final long maxBytes;
    private final ObjectMapper objectMapper;

    public RequestSizeLimitFilter(@Value("${app.request.max-body-bytes:2097152}") long maxBytes,
                                   ObjectMapper objectMapper) {
        this.maxBytes = maxBytes;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        long longitud = request.getContentLengthLong();
        if (longitud > maxBytes) {
            responderPayloadDemasiadoGrande(response, request);
            return;
        }
        // Se envuelve siempre, incluso cuando Content-Length ya vino y era válido: es la
        // única forma de cubrir tanto el caso "sin Content-Length" (chunked) como el caso
        // "Content-Length mintió y el cuerpo real es más grande" (poco probable con
        // clientes reales, pero gratis de cubrir ya que se está envolviendo de todas formas).
        filterChain.doFilter(new LimitingRequestWrapper(request, maxBytes), response);
    }

    private void responderPayloadDemasiadoGrande(HttpServletResponse response, HttpServletRequest request) throws IOException {
        ErrorResponse body = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.PAYLOAD_TOO_LARGE.value(),
                HttpStatus.PAYLOAD_TOO_LARGE.getReasonPhrase(),
                "El cuerpo de la solicitud supera el tamaño máximo permitido",
                request.getRequestURI()
        );
        response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    // Envuelve el InputStream real con uno que cuenta bytes leídos y corta la lectura
    // (lanzando IOException) apenas se supera el límite. Spring MVC lee el cuerpo dentro
    // del manejo normal de la solicitud (HttpMessageConverter), así que esa excepción
    // termina resuelta por GlobalExceptionHandler.handleMensajeIlegible (400) en vez de
    // dejar que el servidor siga leyendo un cuerpo sin límite de tamaño.
    private static final class LimitingRequestWrapper extends HttpServletRequestWrapper {
        private final long maxBytes;

        LimitingRequestWrapper(HttpServletRequest request, long maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new LimitingServletInputStream(super.getInputStream(), maxBytes);
        }
    }

    private static final class LimitingServletInputStream extends ServletInputStream {
        private final ServletInputStream delegate;
        private final long maxBytes;
        private long leidos = 0;

        LimitingServletInputStream(ServletInputStream delegate, long maxBytes) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b != -1) {
                registrar(1);
            }
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = delegate.read(b, off, len);
            if (n > 0) {
                registrar(n);
            }
            return n;
        }

        private void registrar(int bytesLeidos) throws IOException {
            leidos += bytesLeidos;
            if (leidos > maxBytes) {
                throw new IOException("El cuerpo de la solicitud supera el tamaño máximo permitido");
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }
    }
}
