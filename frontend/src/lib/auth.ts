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
    const json = JSON.parse(atob(payload.replace(/-/g, "+").replace(/_/g, "/")));
    return {
      correo: json.sub,
      nombre: json.nombre,
      rol: json.rol,
      exp: json.exp,
    };
  } catch {
    return null;
  }
}

export function guardarToken(token: string) {
  localStorage.setItem(STORAGE_KEY, token);
}

export function obtenerToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(STORAGE_KEY);
}

export function borrarToken() {
  localStorage.removeItem(STORAGE_KEY);
}

export function tokenValido(sesion: SesionUsuario | null): boolean {
  if (!sesion) return false;
  return sesion.exp * 1000 > Date.now();
}
