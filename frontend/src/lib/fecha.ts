// Pedido explícito del usuario (2026-10-31): "ajusta la hora a colombia". El backend siempre
// serializa fecha/hora ya en hora de Bogotá (ver SieJuridicosApplication.main(),
// TimeZone.setDefault("America/Bogota")), pero como string "naive" -- sin 'Z' ni offset, ej.
// "2026-10-31T21:41:05" o, para un LocalDate puro, "2026-10-31". El bug real: casi todo el
// panel hacía `new Date(iso).toLocaleString(...)` SIN fijar ninguna zona -- JavaScript
// interpreta un string así como la hora LOCAL DEL DISPOSITIVO que ejecuta el código, no como
// "ya es hora de Bogotá, muéstrala tal cual". Mientras el dispositivo que ve el panel esté
// en Bogotá, coincide por pura casualidad; en cualquier otro (un abogado viajando, un
// servidor de pruebas, un dispositivo mal configurado) la hora mostrada se corre.
//
// La solución NO es forzar `timeZone: "America/Bogota"` en el toLocaleString() de salida --
// eso solo es correcto si la ENTRADA ya se interpretó como UTC real, y estos strings no lo
// son. La solución real es nunca dejar que `new Date(iso)` interprete nada: se parsean los
// componentes (año/mes/día/hora/minuto/segundo) directamente del string y se construyen con
// el constructor de componentes locales (`new Date(anio, mes, dia, hora, minuto)`), que
// SIEMPRE se interpreta como hora local sin ninguna conversión de por medio. Como los
// componentes YA representan Bogotá, lo que se ve después (sin fijar zona en la salida
// tampoco) es, literalmente, la hora de Bogotá que mandó el backend -- sin importar en qué
// zona horaria esté el dispositivo que mira el panel. Mismo principio que ya usaba
// formatearFechaSolo() en seguimiento-de-caso-modal.tsx antes de esta corrección, generalizado
// aquí para fecha+hora y reutilizado en todo el panel.
function componentesDesde(iso: string): Date {
  const [fecha, horaCompleta] = iso.split("T");
  const [anio, mes, dia] = fecha.split("-").map(Number);
  if (!horaCompleta) {
    return new Date(anio, mes - 1, dia);
  }
  const [horaStr, minutoStr, segundoStr] = horaCompleta.split(":");
  return new Date(anio, mes - 1, dia, Number(horaStr) || 0, Number(minutoStr) || 0, parseInt(segundoStr ?? "0", 10) || 0);
}

export function formatearFechaBogota(
  iso: string,
  opciones: Intl.DateTimeFormatOptions = { day: "numeric", month: "short", year: "numeric" },
): string {
  return componentesDesde(iso).toLocaleDateString("es-CO", opciones);
}

export function formatearFechaHoraBogota(
  iso: string,
  opciones: Intl.DateTimeFormatOptions = {
    day: "numeric",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  },
): string {
  return componentesDesde(iso).toLocaleString("es-CO", opciones);
}

export function formatearHoraBogota(iso: string): string {
  return componentesDesde(iso).toLocaleTimeString("es-CO", { hour: "numeric", minute: "2-digit" });
}

// Para CÁLCULOS de diferencia de tiempo real (ej. "cuántos días faltan para que venza"),
// no solo para mostrar texto -- `componentesDesde(iso).getTime()` da un instante real
// correcto SOLO si el dispositivo está en Bogotá (porque ese Date se construyó como si los
// componentes fueran hora local del dispositivo). Esto en cambio ancla explícitamente la
// entrada como UTC-5 (Colombia no tiene horario de verano, el offset es fijo todo el año) y
// devuelve un timestamp UTC real, comparable sin error contra `Date.now()` sin importar en
// qué zona esté el dispositivo que hace la cuenta.
export function timestampUtcDesdeBogota(iso: string): number {
  const [fecha, horaCompleta] = iso.split("T");
  const [anio, mes, dia] = fecha.split("-").map(Number);
  const [horaStr, minutoStr, segundoStr] = (horaCompleta ?? "00:00:00").split(":");
  return Date.UTC(
    anio,
    mes - 1,
    dia,
    (Number(horaStr) || 0) + 5,
    Number(minutoStr) || 0,
    parseInt(segundoStr ?? "0", 10) || 0,
  );
}
