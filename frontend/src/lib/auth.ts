const STORAGE_KEY = "sie_admin_token";

export type Rol = "ADMIN_GENERAL" | "ABOGADO";

export type SesionUsuario = {
  correo: string;
  nombre: string;
  rol: Rol;
  exp: number;
};

// Decodifica el payload del JWT (sin verificar firma: solo para mostrar datos en la UI,
// la autorización real siempre la vuelve a validar el backend en cada request).
export function decodificarToken(token: string): SesionUsuario | null {
  try {
    const payload = token.split(".")[1];
    if (!payload) return null;
    const json = JSON.parse(atob(payload.replace(/-/g, "+").replace(/_/g, "/")));
    if (!json || typeof json !== "object") return null;
    const correo = typeof json.sub === "string" ? json.sub : "";
    const nombre =
      typeof json.nombre === "string" && json.nombre.trim()
        ? json.nombre
        : correo ? correo.split("@")[0] : "Usuario";
    return {
      correo,
      nombre,
      rol: json.rol === "ADMIN_GENERAL" ? "ADMIN_GENERAL" : "ABOGADO",
      exp: typeof json.exp === "number" ? json.exp : 0,
    };
  } catch {
    return null;
  }
}

export function guardarToken(token: string) {
  try {
    localStorage.setItem(STORAGE_KEY, token);
  } catch {}
}

export function obtenerToken(): string | null {
  if (typeof window === "undefined") return null;
  try {
    return localStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}

export function borrarToken() {
  try {
    localStorage.removeItem(STORAGE_KEY);
  } catch {}
}

export function tokenValido(sesion: SesionUsuario | null): boolean {
  if (!sesion) return false;
  return sesion.exp * 1000 > Date.now();
}
