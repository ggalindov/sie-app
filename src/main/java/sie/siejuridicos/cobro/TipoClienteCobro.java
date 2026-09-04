package sie.siejuridicos.cobro;

// Las dos pestañas del Google Sheets de cobros pendientes de la firma (ver
// HojaCobrosService): EMPRESAS y PERSONAS NATURALES. Misma estructura de columnas en las
// dos, solo cambia la fila donde empieza el encabezado.
public enum TipoClienteCobro {
    EMPRESA("Empresas"),
    PERSONA_NATURAL("Personas Naturales");

    private final String nombreVisible;

    TipoClienteCobro(String nombreVisible) {
        this.nombreVisible = nombreVisible;
    }

    public String getNombreVisible() {
        return nombreVisible;
    }
}
