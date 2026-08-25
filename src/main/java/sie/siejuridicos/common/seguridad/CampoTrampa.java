package sie.siejuridicos.common.seguridad;

// Honeypot para los formularios públicos sin autenticar (solicitudes, testimonios,
// suscripción al boletín). El campo "sitioWeb" no existe visualmente en el formulario
// real (queda oculto vía CSS, nunca display:none/visibility:hidden a secas, ver
// honeypot.tsx en el frontend) así que ningún visitante humano lo llena nunca; un bot
// que autocompleta formularios genéricos sí. No requiere ningún servicio externo
// (reCAPTCHA/hCaptcha) ni credenciales nuevas, y no añade fricción a nadie real.
//
// Cuando se detecta relleno, el controlador responde con un 201 fabricado (mismo status
// que un envío real) SIN tocar la base de datos ni disparar correos: así el bot no
// aprende a distinguir su envío de uno real y no reintenta con otra estrategia.
public final class CampoTrampa {

    private CampoTrampa() {
    }

    public static boolean esBot(String valorCampoTrampa) {
        return valorCampoTrampa != null && !valorCampoTrampa.isBlank();
    }
}
