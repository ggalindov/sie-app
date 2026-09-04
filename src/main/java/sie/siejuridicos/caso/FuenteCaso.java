package sie.siejuridicos.caso;

// De qué hoja (pestaña) del Google Sheets de la firma viene un caso sincronizado -- o
// MANUAL si se creó a mano desde el panel (respaldo, ver CrearCasoRequest), sin ninguna
// hoja detrás. Se usa para: (1) separar los casos por origen en el panel (pedido explícito:
// "cada hoja tenga su separación de casos"), (2) saber qué rango de la hoja consultar en
// vivo cuando un cliente pregunta por su radicado (ver HojaCalculoService), y (3) acotar la
// unicidad de numeroCaso a su propia fuente -- dos hojas distintas pueden tener,
// coincidencia, el mismo "número de caso" interno sin ser el mismo caso.
public enum FuenteCaso {
    JUDICIALES("Judiciales"),
    SUPERINTENDENCIA("Superintendencia"),
    PROCESOS_COMISARIA("Procesos Comisaría"),
    MANUAL("Registrado a mano");

    private final String nombreVisible;

    FuenteCaso(String nombreVisible) {
        this.nombreVisible = nombreVisible;
    }

    public String getNombreVisible() {
        return nombreVisible;
    }
}
