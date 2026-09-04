"use client";

import Image from "next/image";
import Link from "next/link";
import { useEffect, useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { motion } from "motion/react";
import { ArrowLeft, ArrowRight, Spinner } from "@phosphor-icons/react";
import { login as loginRequest, ApiError } from "@/lib/admin-api";
import { useAuth } from "@/lib/auth-context";
import { PasswordInput } from "@/components/admin/password-input";

const EASE = [0.16, 1, 0.3, 1] as const;

// Entrada escalonada de cada bloque de la tarjeta (badge+título, campo de correo, campo de
// contraseña, botón): un delay creciente por índice en vez de animar todo junto de golpe,
// para que la tarjeta se sienta "servida" pieza por pieza en vez de aparecer de un salto.
const bloqueVariants = {
  oculto: { opacity: 0, y: 16 },
  visible: (i: number) => ({
    opacity: 1,
    y: 0,
    transition: { duration: 0.55, ease: EASE, delay: 0.12 + i * 0.09 },
  }),
};

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
    // El resto del panel se queda deliberadamente en la paleta clara (ver CLAUDE.md),
    // pero esta pantalla de entrada es la única que un visitante cualquiera puede
    // encontrarse sin pasar por el candado del nav -- pedido explícito: que se sienta
    // continua con el sitio público (que ahora abre en oscuro por defecto) en vez de
    // un salto brusco a blanco. Degradado animado en vez de un color plano, como en
    // el resto del sitio.
    <div
      className="gradient-animate relative flex min-h-screen items-center justify-center overflow-hidden bg-night px-6 py-16"
      style={{
        backgroundImage:
          "radial-gradient(120% 100% at 15% 0%, rgba(217,169,37,0.16), transparent 55%), radial-gradient(120% 100% at 85% 100%, rgba(63,91,68,0.14), transparent 55%)",
      }}
    >
      <Link
        href="/"
        className="absolute left-6 top-6 z-10 flex items-center gap-2 text-sm text-night-ink/60 transition-colors hover:text-night-ink"
      >
        <ArrowLeft className="h-4 w-4" weight="bold" />
        Volver al inicio
      </Link>

      <motion.div
        initial={{ opacity: 0, y: 26, scale: 0.965 }}
        animate={{ opacity: 1, y: 0, scale: 1 }}
        transition={{ duration: 0.65, ease: EASE }}
        className="relative w-full max-w-sm"
      >
        {/* Halo dorado detrás de la tarjeta, como el resplandor de un gafete bajo luz --
            estático a propósito (no pulsa): la tarjeta ya trae suficiente movimiento propio
            con la entrada escalonada, sumarle un resplandor animado de fondo la saturaría. */}
        <div aria-hidden="true" className="pointer-events-none absolute -inset-8 -z-10 rounded-[3rem] bg-gold/[0.08] blur-3xl" />

        {/* La "tarjetera": panel de vidrio esmerilado con el mismo filo dorado que ya usa
            .card-edged en el resto del sistema (sello de documento legal), pensado como un
            gafete de acceso -- badge del logo, kicker de "acceso exclusivo", campos y botón
            todos dentro de un único contenedor con profundidad real (blur + sombra amplia)
            en vez del formulario suelto sobre el fondo que había antes. */}
        <div className="relative overflow-hidden rounded-[1.75rem] border border-white/10 bg-white/[0.055] px-7 py-9 shadow-[0_45px_100px_-35px_rgba(0,0,0,0.65)] backdrop-blur-2xl sm:px-9">
          <div
            aria-hidden="true"
            className="absolute inset-x-0 top-0 h-[3px]"
            style={{ backgroundImage: "linear-gradient(90deg, var(--color-gold-deep), var(--color-gold) 55%, var(--color-gold-pale))" }}
          />

          <motion.div custom={0} initial="oculto" animate="visible" variants={bloqueVariants} className="flex flex-col items-center">
            <span className="relative flex h-16 w-16 items-center justify-center rounded-full bg-white/[0.06] ring-1 ring-gold/30">
              <span aria-hidden="true" className="absolute inset-0 rounded-full bg-gold/10 blur-md" />
              <Image src="/marca/logo.png" alt="SIE Jurídicos" width={34} height={34} className="relative h-[34px] w-[34px] object-contain" />
            </span>
            <p className="mt-4 text-[11px] font-medium uppercase tracking-[0.22em] text-gold-pale/80">Acceso exclusivo</p>
            <h1 className="mt-1.5 font-display text-2xl text-night-ink">Panel administrativo</h1>
            <p className="mt-1 text-sm text-night-ink/50">SIE Jurídicos</p>
          </motion.div>

          <form onSubmit={onSubmit} className="mt-9 space-y-4">
            <motion.div custom={1} initial="oculto" animate="visible" variants={bloqueVariants} className="space-y-2">
              <label htmlFor="correo" className="text-sm font-medium text-night-ink/70">
                Correo
              </label>
              <input
                id="correo"
                type="email"
                required
                autoFocus
                value={correo}
                onChange={(e) => setCorreo(e.target.value)}
                className="w-full rounded-xl border border-white/15 bg-white/5 px-4 py-3 text-sm text-night-ink placeholder:text-night-ink/30 transition-all duration-300 focus:border-gold-deep/70 focus:bg-white/[0.08] focus:outline-none focus:ring-4 focus:ring-gold/10"
                placeholder="tucorreo@siejuridicos.com"
              />
            </motion.div>

            <motion.div custom={2} initial="oculto" animate="visible" variants={bloqueVariants} className="space-y-2">
              <label htmlFor="contrasena" className="text-sm font-medium text-night-ink/70">
                Contraseña
              </label>
              <PasswordInput
                id="contrasena"
                required
                value={contrasena}
                onChange={(e) => setContrasena(e.target.value)}
                className="w-full rounded-xl border border-white/15 bg-white/5 px-4 py-3 text-sm text-night-ink placeholder:text-night-ink/30 transition-all duration-300 focus:border-gold-deep/70 focus:bg-white/[0.08] focus:outline-none focus:ring-4 focus:ring-gold/10"
                eyeClassName="text-night-ink/50 hover:text-night-ink"
                placeholder="••••••••"
              />
            </motion.div>

            {error && (
              <motion.p
                initial={{ opacity: 0, y: -6 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ duration: 0.3, ease: EASE }}
                className="text-sm text-red-300"
              >
                {error}
              </motion.p>
            )}

            <motion.div custom={3} initial="oculto" animate="visible" variants={bloqueVariants}>
              <button
                type="submit"
                disabled={enviando}
                className="cta-boton flex w-full items-center justify-center gap-2 rounded-lg bg-gold py-3.5 text-sm font-medium text-ink-fixed transition-opacity disabled:opacity-60"
              >
                {enviando ? (
                  <>
                    <Spinner className="admin-loader-anillo h-4 w-4" weight="bold" />
                    Ingresando
                  </>
                ) : (
                  <>
                    Ingresar
                    <ArrowRight className="h-4 w-4" weight="bold" />
                  </>
                )}
              </button>
            </motion.div>
          </form>
        </div>

        <p className="mt-6 text-center text-xs text-night-ink/35">Acceso restringido a personal autorizado de SIE Jurídicos.</p>
      </motion.div>
    </div>
  );
}
