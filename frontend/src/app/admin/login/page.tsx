"use client";

import Image from "next/image";
import Link from "next/link";
import { useEffect, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { ArrowLeft, Spinner } from "@phosphor-icons/react";
import { login as loginRequest, ApiError } from "@/lib/admin-api";
import { useAuth } from "@/lib/auth-context";

export default function LoginPage() {
  const { login, cargando: cargandoSesion, sesion } = useAuth();
  const router = useRouter();
  const [correo, setCorreo] = useState("");
  const [contrasena, setContrasena] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [enviando, setEnviando] = useState(false);

  // La navegación no puede disparase durante el render (violaría las reglas de React);
  // se hace en un efecto una vez la sesión ya existe.
  useEffect(() => {
    if (!cargandoSesion && sesion) {
      router.replace("/admin");
    }
  }, [cargandoSesion, sesion, router]);

  if (!cargandoSesion && sesion) {
    return null;
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setEnviando(true);
    try {
      const respuesta = await loginRequest(correo, contrasena);
      login(respuesta.token);
      router.push("/admin");
    } catch (err) {
      // Muestra el mensaje real del backend cuando existe (p. ej. "Demasiados intentos
      // fallidos, intenta en 13 minuto(s)" de un bloqueo por fuerza bruta): con un
      // mensaje genérico fijo, un admin real bloqueado no tendría forma de saber que
      // no es un simple error de tipeo, ni cuánto debe esperar.
      setError(err instanceof ApiError ? err.message : "Correo o contraseña incorrectos.");
    } finally {
      setEnviando(false);
    }
  }

  return (
    <div className="relative flex min-h-screen items-center justify-center bg-night px-6">
      <Link
        href="/"
        className="absolute left-6 top-6 flex items-center gap-2 text-sm text-night-ink/60 transition-colors hover:text-night-ink"
      >
        <ArrowLeft className="h-4 w-4" weight="bold" />
        Volver al inicio
      </Link>

      <div className="w-full max-w-sm">
        <div className="flex flex-col items-center">
          <Image
            src="/marca/logo.png"
            alt="SIE Jurídicos"
            width={56}
            height={56}
            className="h-14 w-14 object-contain"
          />
          <h1 className="mt-4 font-display text-2xl text-night-ink">
            Panel administrativo
          </h1>
          <p className="mt-1 text-sm text-night-ink/60">SIE Jurídicos</p>
        </div>

        <form onSubmit={onSubmit} className="mt-10 space-y-4">
          <div className="space-y-2">
            <label htmlFor="correo" className="text-sm font-medium text-night-ink/80">
              Correo
            </label>
            <input
              id="correo"
              type="email"
              required
              autoFocus
              value={correo}
              onChange={(e) => setCorreo(e.target.value)}
              className="w-full rounded-xl border border-night-ink/15 bg-night-ink/5 px-4 py-3 text-sm text-night-ink placeholder:text-night-ink/30 focus:border-gold focus:outline-none"
              placeholder="tucorreo@siejuridicos.com"
            />
          </div>

          <div className="space-y-2">
            <label htmlFor="contrasena" className="text-sm font-medium text-night-ink/80">
              Contraseña
            </label>
            <input
              id="contrasena"
              type="password"
              required
              value={contrasena}
              onChange={(e) => setContrasena(e.target.value)}
              className="w-full rounded-xl border border-night-ink/15 bg-night-ink/5 px-4 py-3 text-sm text-night-ink placeholder:text-night-ink/30 focus:border-gold focus:outline-none"
              placeholder="••••••••"
            />
          </div>

          {error && <p className="text-sm text-red-400">{error}</p>}

          <button
            type="submit"
            disabled={enviando}
            className="cta-boton flex w-full items-center justify-center gap-2 rounded-lg bg-gold py-3.5 text-sm font-medium text-ink transition-opacity disabled:opacity-60"
          >
            {enviando && <Spinner className="h-4 w-4 animate-spin" weight="bold" />}
            {enviando ? "Ingresando" : "Ingresar"}
          </button>
        </form>
      </div>
    </div>
  );
}
