package sie.siejuridicos.vigilancia;

// La consulta al portal (listado del despacho o pagina de detalle del boletin) fallo por red,
// timeout o contenido ilegible: NO es lo mismo que "no hay boletin" (resultado valido). El
// llamador debe tratar la busqueda como incompleta y reintentarla mas tarde.
public class ConsultaBoletinFallidaException extends RuntimeException {

    public ConsultaBoletinFallidaException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
