// Honeypot para formularios públicos: un campo que ningún visitante humano puede ver ni
// llenar (fuera de pantalla, sin afectar el layout, ignorado por lectores de pantalla),
// pero que los bots que autocompletan formularios genéricos sí rellenan. Si llega no
// vacío, el backend (ver CampoTrampa.java) responde con un 201 fabricado sin tocar la
// base de datos ni disparar correos, así el bot no aprende a distinguirlo de un envío
// real. `display:none`/`visibility:hidden` se evitan a propósito: algunos bots ya saben
// saltarse esos dos casos específicos; el posicionamiento fuera de pantalla es más difícil
// de detectar automáticamente y sigue siendo invisible para cualquier persona.
export function CampoTrampa() {
  return (
    <input
      type="text"
      name="sitioWeb"
      tabIndex={-1}
      autoComplete="off"
      aria-hidden="true"
      style={{
        position: "absolute",
        left: "-9999px",
        top: "auto",
        width: "1px",
        height: "1px",
        overflow: "hidden",
      }}
    />
  );
}
