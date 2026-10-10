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

// Entrada escalonada de cada bloque del formulario (kicker+título, correo, contraseña,
// botón): un delay creciente por índice en vez de animar todo junto de golpe.
const bloqueVariants = {
  oculto: { opacity: 0, y: 14 },
  visible: (i: number) => ({
    opacity: 1,
    y: 0,
    transition: { duration: 0.55, ease: EASE, delay: 0.1 + i * 0.08 },
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
    // Rediseño completo a pedido explícito del usuario ("quiero genuinamente algo nuevo y
    // diferente, algo elegante y minimalista, no eso tan basico que parece 100% IA"): se
    // abandonó la tarjeta de vidrio centrada con filetes en L y medallón cónico (el cliché
    // genérico de "login con IA") a favor de una composición partida tipo editorial -- la
    // foto de la biblioteca corre de borde a borde de TODA la pantalla (no encerrada en un
    // panel propio ni detrás de una tarjeta), y el contenido se organiza en dos columnas
    // reales: marca/cita a la izquierda, formulario sin caja a la derecha. Sin blur, sin
    // bordes redondeados grandes, sin sombra de tarjeta -- el contraste lo da el propio
    // texto sobre la viñeta, no un contenedor.
    <div className="relative flex min-h-screen flex-col overflow-hidden bg-night md:flex-row">
      <Image
        src="/admin/login-fondo.jpg"
        alt=""
        fill
        priority
        sizes="100vw"
        className="absolute inset-0 object-cover opacity-[0.22]"
      />
      <div
        aria-hidden="true"
        className="absolute inset-0"
        style={{
          backgroundImage:
            "radial-gradient(130% 100% at 20% 0%, transparent 0%, rgba(20,19,15,0.6) 55%, rgba(20,19,15,0.95) 100%)",
        }}
      />
      <div
        aria-hidden="true"
        className="gradient-animate absolute inset-0"
        style={{
          backgroundImage:
            "radial-gradient(110% 90% at 85% 0%, rgba(217,169,37,0.1), transparent 55%), radial-gradient(110% 90% at 10% 100%, rgba(63,91,68,0.1), transparent 55%)",
        }}
      />

      {/* Pedido explícito del usuario: "el boton de regresar lo quiero al borde izquierdo
          pero en la mitad" -- fijo al borde izquierdo de la pantalla, centrado verticalmente,
          no arriba. El texto se oculta en pantallas muy angostas para no chocar con el
          formulario. */}
      <Link
        href="/"
        className="fixed left-5 top-1/2 z-30 flex -translate-y-1/2 items-center gap-2 text-sm text-night-ink/45 transition-colors hover:text-night-ink md:left-8"
      >
        <ArrowLeft className="h-4 w-4" weight="bold" />
        <span className="hidden sm:inline">Volver al inicio</span>
      </Link>

      {/* Columna izquierda: marca + cita, solo escritorio. No es un panel con imagen propia
          -- comparte la misma foto de fondo de toda la pantalla, así que basta con el texto
          flotando sobre ella. */}
      <div className="relative z-10 hidden w-[42%] flex-col justify-between p-14 md:flex lg:w-[45%] lg:p-20">
        <Image src="/marca/logo.png" alt="SIE Jurídicos" width={40} height={35} className="h-9 w-auto object-contain opacity-90" />
        <div>
          {/* "20+ años" es la misma cifra real ya usada en la sección "Con la confianza de"
              del sitio público (ver trusted-by.tsx) -- no se inventa ningún dato nuevo aquí. */}
          <p className="max-w-sm text-balance font-display text-3xl leading-[1.15] text-night-ink/95 lg:text-4xl">
            Más de veinte años acompañando procesos jurídicos en Bogotá.
          </p>
          <p className="mt-5 text-sm text-night-ink/40">SIE Jurídicos · Panel interno</p>
        </div>
      </div>

      {/* Separador: un filete, no un borde de tarjeta -- la única frontera visual entre las
          dos mitades de la pantalla. */}
      <div aria-hidden="true" className="relative z-10 hidden w-px self-stretch bg-gradient-to-b from-transparent via-gold/20 to-transparent md:block" />

      <div className="relative z-10 flex flex-1 items-center justify-center px-7 py-20 sm:px-10 md:px-14 lg:px-16">
        <div className="w-full max-w-sm">
          {/* Equivalente compacto de la columna izquierda, solo en móvil (esa columna está
              oculta ahí). */}
          <div className="mb-10 flex items-center gap-3 md:hidden">
            <Image src="/marca/logo.png" alt="SIE Jurídicos" width={32} height={28} className="h-7 w-auto object-contain opacity-90" />
            <span className="text-sm text-night-ink/45">SIE Jurídicos · Panel interno</span>
          </div>

          <motion.div custom={0} initial="oculto" animate="visible" variants={bloqueVariants}>
            <p className="text-xs font-medium uppercase tracking-[0.3em] text-gold-pale/70">Acceso interno</p>
            <h1 className="mt-3 font-display text-4xl text-night-ink">Panel administrativo</h1>
            <p className="mt-3 text-sm text-night-ink/45">Ingresa con tus credenciales de la firma.</p>
          </motion.div>

          <form onSubmit={onSubmit} className="mt-11 space-y-7">
            {/* Campos tipo "subrayado" (sin caja, sin fondo) en vez de inputs encerrados --
                el lenguaje minimalista que pidió el usuario, consistente con el resto de la
                composición sin tarjetas. */}
            <motion.div custom={1} initial="oculto" animate="visible" variants={bloqueVariants} className="space-y-2">
              <label htmlFor="correo" className="text-xs font-medium uppercase tracking-wide text-night-ink/40">
                Correo
              </label>
              <input
                id="correo"
                type="email"
                required
                autoFocus
                value={correo}
                onChange={(e) => setCorreo(e.target.value)}
                className="w-full border-0 border-b border-night-ink/15 bg-transparent px-0 py-2.5 text-night-ink placeholder:text-night-ink/25 transition-colors duration-300 focus:border-gold focus:outline-none"
                placeholder="tucorreo@siejuridicos.com"
              />
            </motion.div>

            <motion.div custom={2} initial="oculto" animate="visible" variants={bloqueVariants} className="space-y-2">
              <label htmlFor="contrasena" className="text-xs font-medium uppercase tracking-wide text-night-ink/40">
                Contraseña
              </label>
              <PasswordInput
                id="contrasena"
                required
                value={contrasena}
                onChange={(e) => setContrasena(e.target.value)}
                className="w-full border-0 border-b border-night-ink/15 bg-transparent px-0 py-2.5 text-night-ink placeholder:text-night-ink/25 transition-colors duration-300 focus:border-gold focus:outline-none"
                eyeClassName="text-night-ink/40 hover:text-night-ink"
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

            <motion.div custom={3} initial="oculto" animate="visible" variants={bloqueVariants} className="pt-2">
              <button
                type="submit"
                disabled={enviando}
                className="cta-boton flex w-full items-center justify-center gap-2 rounded-full bg-gold py-3.5 text-sm font-medium text-ink-fixed transition-opacity disabled:opacity-60"
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

          <p className="mt-10 text-xs text-night-ink/30">Acceso restringido a personal autorizado de SIE Jurídicos.</p>
        </div>
      </div>
    </div>
  );
}
