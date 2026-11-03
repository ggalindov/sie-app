package sie.siejuridicos.vigilancia;

// La descarga de un PDF de Publicaciones Procesales no se pudo completar. reintentable=true:
// fallo de red/timeout/5xx, vale la pena volver a intentarlo (mas tarde o en otro ciclo);
// false: el documento no existe o la URL no es valida/permitida, reintentar no sirve.
public class DescargaDocumentoFallidaException extends RuntimeException {

    private final String url;
    private final boolean reintentable;

    public DescargaDocumentoFallidaException(String url, boolean reintentable, String mensaje, Throwable causa) {
        super(mensaje, causa);
        this.url = url;
        this.reintentable = reintentable;
    }

    public String getUrl() {
        return url;
    }

    public boolean isReintentable() {
        return reintentable;
    }
}
