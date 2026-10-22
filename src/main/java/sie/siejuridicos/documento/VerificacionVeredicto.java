package sie.siejuridicos.documento;

// Resultado de una verificación pública anti-alteración.
public enum VerificacionVeredicto {
    // El documento o archivo coincide con lo registrado y ninguna capa de evidencia se rompió.
    INTEGRO,
    // Hay evidencia de que el archivo o su registro fue modificado.
    ALTERADO,
    // SIE no tiene ningún registro que corresponda a lo consultado.
    NO_REGISTRADO
}
