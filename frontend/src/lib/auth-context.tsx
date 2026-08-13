"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
  type ReactNode,
} from "react";
import { useRouter } from "next/navigation";
import {
  borrarToken,
  decodificarToken,
  guardarToken,
  obtenerToken,
  tokenValido,
  type SesionUsuario,
} from "@/lib/auth";

type AuthContextValue = {
  sesion: SesionUsuario | null;
  token: string | null;
  cargando: boolean;
  login: (token: string) => void;
  logout: () => void;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setToken] = useState<string | null>(null);
  const [sesion, setSesion] = useState<SesionUsuario | null>(null);
  const [cargando, setCargando] = useState(true);
  const router = useRouter();

  useEffect(() => {
    const guardado = obtenerToken();
    if (guardado) {
      const decodificada = decodificarToken(guardado);
      if (tokenValido(decodificada)) {
        setToken(guardado);
        setSesion(decodificada);
      } else {
        borrarToken();
      }
    }
    setCargando(false);
  }, []);

  const login = useCallback((nuevoToken: string) => {
    guardarToken(nuevoToken);
    setToken(nuevoToken);
    setSesion(decodificarToken(nuevoToken));
  }, []);

  const logout = useCallback(() => {
    borrarToken();
    setToken(null);
    setSesion(null);
    router.push("/admin/login");
  }, [router]);

  return (
    <AuthContext.Provider value={{ sesion, token, cargando, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth debe usarse dentro de <AuthProvider>");
  return ctx;
}
