package sie.siejuridicos.usuario.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no tiene un formato válido")
        String correo,

        @NotBlank(message = "La contraseña es obligatoria")
        String contrasena,

        // Pedido explícito del usuario: "captcha al login" -- token de Cloudflare
        // Turnstile, verificado en AuthService.login() contra TurnstileService ANTES de
        // tocar la base de datos. Sin @NotBlank a propósito: si app.turnstile.secret-key
        // no está configurada (entorno de desarrollo sin esa clave), TurnstileService.
        // verificar() deja pasar sin exigir nada -- un @NotBlank aquí rompería el login en
        // ese caso, antes de llegar siquiera a esa lógica.
        String recaptchaToken
) {
}
