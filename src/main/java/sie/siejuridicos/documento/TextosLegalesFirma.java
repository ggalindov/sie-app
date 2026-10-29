package sie.siejuridicos.documento;

// Textos legales fijos usados en más de un punto del motor de firma (hoy solo la
// advertencia de suplantación, punto 2 de las mejoras pedidas sobre el motor "hermano"
// UCN). Centralizados aquí para que el texto EXACTO que el firmante aceptó (ver
// DocumentoFirmante.registrarAdvertenciaSuplantacion*) coincida siempre con lo que de
// verdad se le mostró, sin duplicar la redacción en el controlador ni en el servicio.
//
// Artículo 296 del Código Penal (Ley 599 de 2000, "falsedad personal"): tipifica
// exactamente suplantar o sustituir a una persona para obtener un provecho para sí o para
// un tercero -- verificado antes de citarlo (ver discusión en el fork que escribió esta
// clase), no es una cita genérica sin confirmar.
public final class TextosLegalesFirma {

    public static final String ADVERTENCIA_SUPLANTACION_IDENTIDAD =
            "Antes de continuar, declaro que la fotografía que voy a tomar corresponde al documento de "
                    + "identidad que tengo físicamente en mis manos en este momento, tomada en vivo con la cámara "
                    + "de este dispositivo -- no una foto guardada, una captura de pantalla ni el documento de otra "
                    + "persona. Entiendo que suplantar o sustituir la identidad de otra persona para obtener un "
                    + "provecho propio o de un tercero puede constituir el delito de falsedad personal (artículo "
                    + "296 del Código Penal, Ley 599 de 2000), y que esta aceptación queda registrada como parte "
                    + "de la evidencia del proceso de firma.";

    public static final String ADVERTENCIA_SUPLANTACION_VERIFICACION =
            "Antes de continuar, declaro que la fotografía que voy a tomar es una selfie mía, tomada en vivo "
                    + "con la cámara de este dispositivo en este momento -- no la foto de otra persona ni una "
                    + "imagen guardada. Entiendo que suplantar o sustituir la identidad de otra persona para "
                    + "obtener un provecho propio o de un tercero puede constituir el delito de falsedad personal "
                    + "(artículo 296 del Código Penal, Ley 599 de 2000), y que esta aceptación queda registrada "
                    + "como parte de la evidencia del proceso de firma.";

    private TextosLegalesFirma() {
    }
}
