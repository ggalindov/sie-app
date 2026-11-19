package sie.siejuridicos.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex, HttpServletRequest request) {
        return construir(ex.getStatus(), ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String mensaje = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return construir(HttpStatus.BAD_REQUEST, mensaje, request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        return construir(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErrorResponse> handleDisabled(DisabledException ex, HttpServletRequest request) {
        return construir(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return construir(HttpStatus.FORBIDDEN, "No tiene permisos para realizar esta acción", request);
    }

    // Sin estos handlers específicos, el @ExceptionHandler(Exception.class) genérico de más
    // abajo intercepta también estos casos (Spring resuelve @ExceptionHandler antes que sus
    // propios resolvers por defecto), convirtiendo errores de forma de la solicitud (JSON mal
    // formado, parámetro con tipo incorrecto, método HTTP no soportado, ruta inexistente) en
    // un 500 "Ocurrió un error inesperado" en vez del 4xx correcto y más útil para el cliente.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMensajeIlegible(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return construir(HttpStatus.BAD_REQUEST, "El cuerpo de la solicitud no es un JSON válido", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTipoInvalido(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return construir(HttpStatus.BAD_REQUEST,
                "El parámetro '" + ex.getName() + "' tiene un valor con formato inválido", request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleParametroFaltante(MissingServletRequestParameterException ex, HttpServletRequest request) {
        return construir(HttpStatus.BAD_REQUEST, "Falta el parámetro obligatorio '" + ex.getParameterName() + "'", request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMetodoNoSoportado(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return construir(HttpStatus.METHOD_NOT_ALLOWED, "El método " + ex.getMethod() + " no está soportado en esta ruta", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleRecursoNoEncontrado(NoResourceFoundException ex, HttpServletRequest request) {
        return construir(HttpStatus.NOT_FOUND, "El recurso solicitado no existe", request);
    }

    // Content-Type no soportado (ej. text/plain o application/xml en un endpoint que
    // solo acepta JSON): es un error del cliente (415), no un fallo interno del servidor.
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNoSoportado(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return construir(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "El tipo de contenido enviado no está soportado", request);
    }

    // Postgres caído, pool de conexiones agotado, restricción de columna violada sin
    // traducir por ErroresBaseDatos, etc. Se responde 503 (el problema es transitorio y
    // externo, no un bug de la petición) en vez de dejar que caiga en el 500 genérico,
    // sin filtrar mensaje de la base de datos ni del driver JDBC.
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponse> handleBaseDatos(DataAccessException ex, HttpServletRequest request) {
        log.error("Error de acceso a la base de datos en {}", request.getRequestURI(), ex);
        return construir(HttpStatus.SERVICE_UNAVAILABLE,
                "El servicio no está disponible en este momento. Intente de nuevo más tarde", request);
    }

    // Caida de produccion 2026-11-19: tras un OutOfMemoryError el hilo que acepta conexiones
    // HTTP murio y el proceso siguio "vivo" pero sordo durante horas (Docker no lo reinicia si
    // la JVM no termina). Un OOM es irrecuperable: se responde 503 y se baja la JVM a
    // proposito para que Docker (restart: unless-stopped) la levante limpia en segundos.
    @ExceptionHandler(OutOfMemoryError.class)
    public ResponseEntity<ErrorResponse> handleSinMemoria(OutOfMemoryError ex, HttpServletRequest request) {
        return responderSinMemoria(ex, request);
    }

    // El contenedor de servlets suele envolver el Error en una ServletException
    // ("Handler dispatch failed: java.lang.OutOfMemoryError").
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        for (Throwable t = ex; t != null; t = (t.getCause() == t ? null : t.getCause())) {
            if (t instanceof OutOfMemoryError oom) {
                return responderSinMemoria(oom, request);
            }
        }
        log.error("Error no controlado en {}", request.getRequestURI(), ex);
        return construir(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error inesperado", request);
    }

    private ResponseEntity<ErrorResponse> responderSinMemoria(OutOfMemoryError ex, HttpServletRequest request) {
        // Sin stacktrace completo a proposito: construirlo tambien necesita memoria.
        log.error("[CRITICO] OutOfMemoryError en {}: {}. Se reinicia la JVM para recuperar el servicio.",
                request.getRequestURI(), ex.getMessage());
        Thread apagado = new Thread(() -> {
            try {
                Thread.sleep(1500); // deja salir esta respuesta 503 antes de caer
            } catch (InterruptedException ignorada) {
                Thread.currentThread().interrupt();
            }
            Runtime.getRuntime().halt(137);
        }, "apagado-por-oom");
        apagado.setDaemon(true);
        apagado.start();
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ErrorResponse(
                LocalDateTime.now(), HttpStatus.SERVICE_UNAVAILABLE.value(),
                HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
                "El servicio se está reiniciando. Intente de nuevo en unos segundos", request.getRequestURI()));
    }

    private ResponseEntity<ErrorResponse> construir(HttpStatus status, String mensaje, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                mensaje,
                request.getRequestURI()
        );
        return ResponseEntity.status(status).body(body);
    }
}
