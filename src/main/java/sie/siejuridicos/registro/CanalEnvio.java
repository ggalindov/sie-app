package sie.siejuridicos.registro;

// Por qué medio salió el mensaje -- ver RegistroEnvio.
public enum CanalEnvio {
    EMAIL("Correo"),
    WHATSAPP("WhatsApp");

    private final String nombreVisible;

    CanalEnvio(String nombreVisible) {
        this.nombreVisible = nombreVisible;
    }

    public String getNombreVisible() {
        return nombreVisible;
    }
}
