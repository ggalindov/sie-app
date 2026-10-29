package sie.siejuridicos.pqrs;

public enum TipoPqrs {
    PETICION("Petición"),
    QUEJA("Queja"),
    RECLAMO("Reclamo"),
    SUGERENCIA("Sugerencia");

    private final String nombreVisible;

    TipoPqrs(String nombreVisible) {
        this.nombreVisible = nombreVisible;
    }

    public String getNombreVisible() {
        return nombreVisible;
    }
}
