"use client";

import { useEffect, useRef, type ReactNode, type CSSProperties } from "react";
import Link from "next/link";
import { Lexend, Poppins } from "next/font/google";
import { motion, useInView, useMotionValue, useScroll, useTransform } from "motion/react";
import { animate } from "motion";
import {
  ArrowRight,
  Certificate,
  Gavel,
  MagnifyingGlass,
  Quotes,
  Scales,
  ShieldCheck,
  WarningCircle,
} from "@phosphor-icons/react";
import { MagneticButton } from "@/components/magnetic-button";
import { SealRing } from "@/components/seal-ring";
import { MascotaSaludo, MascotaFeliz, MascotaPreocupada } from "@/components/mascota-escudo";

// Tipografía propia de esta página: Lexend (titulares) + Poppins (cuerpo), las mismas
// dos familias que usa cuidatumarca.siejuridicos.com (confirmado inspeccionando su CSS
// calculado), ambas de licencia abierta en Google Fonts. Con alcance SOLO a esta página:
// el resto del sitio usa Playfair Display + Manrope como identidad de marca ya
// establecida (ver globals.css/layout.tsx), y esta es una landing gemela con su propia
// identidad visual, no un cambio de la tipografía del sitio.
const lexend = Lexend({ subsets: ["latin"], weight: ["500", "600", "700"] });
const poppins = Poppins({ subsets: ["latin"], weight: ["400", "500", "600"] });

const EASE = [0.16, 1, 0.3, 1] as const;

// Página gemela de la que la firma ya tenía en cuidatumarca.siejuridicos.com
// (otro dominio, mismo negocio). Es solo visual: no lleva formulario propio,
// todo CTA reenvía a la sección "Agendar asesoría" de la home, que es el
// único formulario real conectado al backend (ver agendar-asesoria.tsx).
const CTA_HREF = "/#agendar";

// Misma familia de degradados ya usada en areas-practica.tsx (probada, con
// buen contraste): reutilizarla en vez de inventar una paleta nueva mantiene
// esta página gemela dentro de la misma disciplina cromática de la marca.
// night/night-ink/gold/ink-fixed son tokens fijos que no cambian con el
// toggle de tema, así esta página conserva su identidad oscura siempre,
// igual que el Hero de la home ya hace con su video de fondo.
const MOOD_DEEP = "linear-gradient(150deg, #0a0906 0%, #1c1811 55%, #0a0906 100%)";
const MOOD_BRONZE = "linear-gradient(160deg, #221c12 0%, #3d321c 60%, #221c12 100%)";
const MOOD_GOLD = "linear-gradient(135deg, #a97c16 0%, #d9a925 50%, #f1cf6a 100%)";
const MOOD_RISK = "linear-gradient(160deg, #241714 0%, #3a201a 55%, #241714 100%)";

function reveal(delay = 0) {
  return {
    initial: { opacity: 0, y: 24 },
    whileInView: { opacity: 1, y: 0 },
    viewport: { once: true, amount: 0.3 },
    transition: { duration: 0.7, delay, ease: EASE },
  } as const;
}

// Flotación idle suave, siempre disponible para las mascotas: MotionConfig
// reducedMotion="user" en el layout raíz ya la neutraliza sola cuando el
// usuario pide menos movimiento, no hace falta condicionarla aquí.
function Flotante({
  children,
  delay = 0,
  distancia = 10,
  duracion = 4.2,
}: {
  children: ReactNode;
  delay?: number;
  distancia?: number;
  duracion?: number;
}) {
  return (
    <motion.div
      animate={{ y: [0, -distancia, 0] }}
      transition={{ duration: duracion, delay, repeat: Infinity, ease: "easeInOut" }}
    >
      {children}
    </motion.div>
  );
}

function Eyebrow({ children, tone = "gold" }: { children: ReactNode; tone?: "gold" | "risk" }) {
  return (
    <span
      className={`inline-flex items-center rounded-full px-3 py-1 text-[11px] font-medium uppercase tracking-[0.18em] ${
        tone === "gold"
          ? "bg-gold/12 text-gold ring-1 ring-gold/25"
          : "bg-red-400/10 text-red-300 ring-1 ring-red-400/25"
      }`}
    >
      {children}
    </span>
  );
}

function CtaButton({ children, href = CTA_HREF }: { children: ReactNode; href?: string }) {
  return (
    <MagneticButton strength={0.4}>
      <Link
        href={href}
        className="cta-boton group inline-flex items-center gap-3 rounded-lg bg-gold px-7 py-4 text-base font-medium text-ink-fixed active:scale-[0.98]"
      >
        {children}
        <ArrowRight
          weight="bold"
          className="h-4 w-4 shrink-0 transition-transform duration-300 group-hover:translate-x-1"
        />
      </Link>
    </MagneticButton>
  );
}

function Contador({ numero, prefijo = "", sufijo = "" }: { numero: number; prefijo?: string; sufijo?: string }) {
  const ref = useRef<HTMLSpanElement>(null);
  const enVista = useInView(ref, { once: true, amount: 0.6 });
  const valor = useMotionValue(0);

  useEffect(() => {
    if (!enVista) return;
    const controles = animate(valor, numero, {
      duration: 1.6,
      ease: EASE,
      onUpdate: (v) => {
        if (ref.current) ref.current.textContent = `${prefijo}${Math.round(v)}${sufijo}`;
      },
    });
    return () => controles.stop();
  }, [enVista, numero, prefijo, sufijo, valor]);

  return (
    <span ref={ref}>
      {prefijo}0{sufijo}
    </span>
  );
}

const stats = [
  { numero: 75, sufijo: "+", etiqueta: "Marcas protegidas con éxito" },
  { numero: 80, sufijo: "+", etiqueta: "Empresarios acompañados en el proceso legal" },
  { numero: 99, sufijo: "%", etiqueta: "Tasa de satisfacción en asesoría legal" },
  { numero: 100, sufijo: "%", etiqueta: "Comprometidos con que tu marca sea solo tuya" },
];

const beneficios = [
  {
    icono: MagnifyingGlass,
    titulo: "Viabilidad legal",
    descripcion: "Consulta y análisis de viabilidad legal del nombre antes de radicar nada.",
  },
  {
    icono: Certificate,
    titulo: "Registro oficial",
    descripcion: "Registro ante la Superintendencia de Industria y Comercio, de principio a fin.",
  },
  {
    icono: Scales,
    titulo: "Asesoría jurídica",
    descripcion: "Acompañamiento jurídico claro durante todo el proceso, sin enredos.",
  },
  {
    icono: Gavel,
    titulo: "Defensa legal",
    descripcion: "Defensa de tu marca en caso de plagio o uso indebido por parte de terceros.",
  },
];

const consecuencias = [
  {
    titulo: "Alguien más puede registrarla antes que tú.",
    descripcion:
      "Aunque tú la hayas usado primero, si no está registrada, legalmente no te pertenece.",
  },
  {
    titulo: "Podrías recibir una demanda.",
    descripcion:
      "Sí, incluso sin intención, por usar un nombre que ya tiene dueño. Podrías verte obligado a cambiar todo: logo, nombre, redes y más.",
  },
  {
    titulo: "No podrás defenderte si alguien la copia.",
    descripcion: "Sin registro, no tienes herramientas legales reales para proteger lo tuyo.",
  },
  {
    titulo: "Pierdes valor como empresa.",
    descripcion:
      "Una marca sin registrar no es un activo. Si quieres crecer, licenciar o vender tu negocio, eso será un problema.",
  },
];

const marcaProtegida = [
  "Tienes el derecho exclusivo sobre tu nombre y tu logo.",
  "Puedes actuar legalmente si alguien copia tu marca.",
  "Tu marca es un activo real: se puede crecer, licenciar o vender.",
  "Transmites seriedad y confianza a tus clientes.",
];

const marcaNoProtegida = [
  "Cualquiera puede registrarla legalmente antes que tú.",
  "No tienes cómo defenderte si alguien la usa o la copia.",
  "Podrías tener que cambiar nombre, logo y redes de un día para otro.",
  "No es un activo: no puedes licenciarla ni venderla.",
];

const MARQUEE_ITEMS = Array.from({ length: 8 }, (_, i) => i);

export function CuidaMarcaLanding() {
  // Parallax local del Hero (mismo patrón que criterio.tsx: useScroll con
  // target propio, no el scroll global de la página) para que el halo
  // dorado respire con el scroll en vez de quedar pegado al fondo.
  const heroRef = useRef<HTMLElement>(null);
  const { scrollYProgress: heroProgress } = useScroll({
    target: heroRef,
    offset: ["start start", "end start"],
  });
  const heroGlowY = useTransform(heroProgress, [0, 1], ["0%", "26%"]);

  // Parallax local del glifo ® gigante de la sección "por qué importa".
  const whyRef = useRef<HTMLElement>(null);
  const { scrollYProgress: whyProgress } = useScroll({
    target: whyRef,
    offset: ["start end", "end start"],
  });
  const whyGlyphY = useTransform(whyProgress, [0, 1], ["-8%", "8%"]);

  return (
    <main
      className={`flex-1 bg-night text-night-ink ${poppins.className}`}
      style={{ "--font-display": lexend.style.fontFamily } as CSSProperties}
    >
      {/* Hero: idéntico criterio al de la home, video-menos pero mismo peso
          visual (gradiente oscuro de borde a borde + halos dorados), para
          que el nav (transparente al llegar) siga leyéndose bien encima. */}
      <section
        ref={heroRef}
        className="relative flex min-h-[100dvh] items-center justify-center overflow-hidden"
        style={{ backgroundImage: MOOD_DEEP }}
      >
        <motion.div
          aria-hidden="true"
          style={{ y: heroGlowY }}
          className="gradient-animate absolute inset-[-10%_0_0_0]"
        >
          <div
            className="h-full w-full"
            style={{
              backgroundImage:
                "radial-gradient(900px circle at 18% 12%, rgba(217,169,37,0.22), transparent 55%), radial-gradient(800px circle at 85% 82%, rgba(241,207,106,0.14), transparent 55%)",
            }}
          />
        </motion.div>
        <div className="relative mx-auto flex max-w-3xl flex-col items-center px-6 pb-16 pt-32 text-center md:pt-24">
          <motion.div {...reveal(0)}>
            <Eyebrow>SIE Jurídicos · Protección de marca</Eyebrow>
          </motion.div>

          <motion.h1
            {...reveal(0.12)}
            className="mt-7 text-balance font-display text-4xl leading-[1.08] tracking-tight md:text-6xl lg:text-7xl"
          >
            ¿Y si mañana otra empresa registra el nombre de tu negocio?
          </motion.h1>

          <motion.p {...reveal(0.24)} className="mt-6 max-w-xl text-balance text-lg leading-relaxed text-night-ink/75">
            Registra tu marca fácilmente y sin complicaciones legales, con asesoría experta
            y la consulta es <strong className="font-semibold text-gold">GRATIS</strong>.
          </motion.p>

          <motion.p {...reveal(0.32)} className="mt-3 max-w-lg text-balance text-base leading-relaxed text-night-ink/60">
            Te lo explicamos fácil, sin enredos y con todo lo que necesitas saber para
            proteger tu negocio desde hoy.
          </motion.p>

          <motion.div {...reveal(0.42)} className="mt-10">
            <CtaButton>Protege tu marca ahora</CtaButton>
          </motion.div>
        </div>
      </section>

      {/* Marquee: único de la página, mismo criterio que TrustedBy en la home
          (máximo un marquee por página, incluso en esta gemela). */}
      <div className="overflow-hidden border-y border-gold/15 bg-[#0a0906] py-4">
        <div className="flex w-max animate-marquee items-center gap-10">
          {[...MARQUEE_ITEMS, ...MARQUEE_ITEMS].map((i, idx) => (
            <span key={`${i}-${idx}`} className="flex items-center gap-10 whitespace-nowrap">
              <span className="font-display text-lg text-gold/80">Cuidamos tu marca</span>
              <ShieldCheck weight="fill" className="h-3.5 w-3.5 text-gold/40" />
            </span>
          ))}
        </div>
      </div>

      {/* Sub-hero: split texto + mascota, sobre bronce oscuro. La mascota es
          nuestra propia ilustración (ver mascota-escudo.tsx): sin generador
          de imágenes disponible, se optó por un personaje vectorial propio
          en vez de imitar el estilo fotorrealista del sitio original. El
          anillo que se traza (SealRing, GSAP aislado) refuerza "sello
          oficial", igual que ya hace en el logo de la home. */}
      <section className="relative overflow-hidden py-24 md:py-28" style={{ backgroundImage: MOOD_BRONZE }}>
        <div className="mx-auto grid max-w-6xl gap-14 px-6 md:grid-cols-12 md:items-center md:gap-10">
          <motion.div {...reveal(0)} className="md:col-span-7">
            <Eyebrow>Marca 100% tuya</Eyebrow>
            <h2 className="mt-5 text-balance font-display text-3xl leading-tight tracking-tight md:text-5xl">
              Haz que tu marca sea solo tuya
            </h2>
            <p className="mt-5 max-w-lg text-balance text-base leading-relaxed text-night-ink/70">
              Registrar tu marca con nosotros es contar con un equipo que habla claro, que
              te acompaña en cada paso y que se toma tu negocio tan en serio como tú.
            </p>
            <div className="mt-9">
              <CtaButton>Regístrala ya</CtaButton>
            </div>
          </motion.div>

          <motion.div
            initial={{ opacity: 0, scale: 0.85, rotate: -6 }}
            whileInView={{ opacity: 1, scale: 1, rotate: 0 }}
            viewport={{ once: true, amount: 0.4 }}
            transition={{ duration: 0.9, ease: EASE }}
            className="md:col-span-5"
          >
            <Flotante distancia={12} duracion={5}>
              <div className="relative mx-auto aspect-square w-full max-w-xs">
                <SealRing delay={0.3} />
                <div
                  className="absolute inset-[11%] flex items-center justify-center rounded-full ring-1 ring-gold/20"
                  style={{ backgroundImage: MOOD_GOLD, boxShadow: "0 30px 60px -24px rgba(169,124,22,0.55)" }}
                >
                  <MascotaSaludo className="h-[86%] w-[86%]" />
                </div>
              </div>
            </Flotante>
          </motion.div>
        </div>
      </section>

      {/* Cifras: único panel dorado brillante, el acento agudo de la página */}
      <section className="relative overflow-hidden py-20" style={{ backgroundImage: MOOD_GOLD }}>
        <div className="mx-auto grid max-w-6xl grid-cols-2 gap-x-6 gap-y-10 px-6 md:grid-cols-4">
          {stats.map((s, i) => (
            <motion.div key={s.etiqueta} {...reveal(0.05 * i)} className="text-center md:text-left">
              <p className="font-display text-5xl tracking-tight text-ink-fixed md:text-6xl">
                <Contador numero={s.numero} sufijo={s.sufijo} />
              </p>
              <p className="mt-2 text-sm leading-snug text-ink-fixed/70">{s.etiqueta}</p>
            </motion.div>
          ))}
        </div>
      </section>

      {/* Cómo ayudamos: explicación + 4 beneficios */}
      <section className="relative overflow-hidden py-24 md:py-28" style={{ backgroundImage: MOOD_BRONZE }}>
        <div className="mx-auto max-w-6xl px-6">
          <div className="grid gap-14 md:grid-cols-12 md:gap-10">
            <motion.div {...reveal(0)} className="md:col-span-5">
              <Eyebrow>Cómo te acompañamos</Eyebrow>
              <h2 className="mt-5 text-balance font-display text-3xl leading-tight tracking-tight md:text-4xl">
                ¿Te preguntas cómo te ayudamos a proteger tu marca?
              </h2>
              <p className="mt-5 text-balance text-base leading-relaxed text-night-ink/70">
                Registrar tu marca no es solo un papel o un trámite. Es asegurarte de que
                lo que estás creando es realmente tuyo. Cuando tu marca está protegida,
                puedes crecer sin miedo y enfocarte en hacerla brillar.
              </p>
              <div className="mt-6 flex gap-3 border-l-2 border-gold/40 pl-4">
                <Quotes weight="fill" className="mt-1 h-5 w-5 shrink-0 text-gold/50" />
                <p className="font-display text-lg italic leading-snug text-night-ink/80">
                  Esto me pertenece y nadie puede quitármelo.
                </p>
              </div>
            </motion.div>

            <div className="grid gap-5 sm:grid-cols-2 md:col-span-7">
              {beneficios.map((b, i) => (
                <motion.div
                  key={b.titulo}
                  {...reveal(0.06 * i)}
                  whileHover={{ y: -5 }}
                  className="rounded-2xl bg-white/[0.03] p-6 ring-1 ring-white/10 transition-shadow duration-300 hover:ring-gold/25"
                >
                  <span
                    className="flex h-11 w-11 items-center justify-center rounded-full text-gold"
                    style={{ backgroundImage: MOOD_DEEP }}
                  >
                    <b.icono weight="duotone" className="h-5 w-5" />
                  </span>
                  <h3 className="mt-4 font-display text-lg leading-snug">{b.titulo}</h3>
                  <p className="mt-1.5 text-sm leading-relaxed text-night-ink/60">{b.descripcion}</p>
                </motion.div>
              ))}
            </div>
          </div>
        </div>
      </section>

      {/* Consecuencias de no registrar */}
      <section className="relative overflow-hidden py-24 md:py-28" style={{ backgroundImage: MOOD_DEEP }}>
        <div className="mx-auto max-w-4xl px-6">
          <motion.div {...reveal(0)} className="text-center">
            <Eyebrow tone="risk">El riesgo real</Eyebrow>
            <h2 className="mt-5 text-balance font-display text-3xl leading-tight tracking-tight md:text-5xl">
              ¿Qué pasa si no registro mi marca?
            </h2>
          </motion.div>

          <div className="mt-14 grid gap-5 sm:grid-cols-2">
            {consecuencias.map((c, i) => (
              <motion.div
                key={c.titulo}
                {...reveal(0.06 * i)}
                whileHover={{ y: -5 }}
                className="flex gap-4 rounded-2xl bg-white/[0.03] p-6 ring-1 ring-white/10 transition-shadow duration-300 hover:ring-red-400/20"
              >
                <WarningCircle weight="fill" className="mt-0.5 h-6 w-6 shrink-0 text-red-300/80" />
                <div>
                  <h3 className="font-display text-lg leading-snug">{c.titulo}</h3>
                  <p className="mt-1.5 text-sm leading-relaxed text-night-ink/60">{c.descripcion}</p>
                </div>
              </motion.div>
            ))}
          </div>

          <motion.div {...reveal(0.2)} className="mt-12 flex justify-center">
            <CtaButton>Quiero proteger mi marca</CtaButton>
          </motion.div>
        </div>
      </section>

      {/* Comparación: ¿cuál quieres ser tú? Cada lado con su propia mascota
          (feliz / preocupada), la alusión más directa a la comparación
          "protegida vs. no protegida" del sitio original. */}
      <section className="relative overflow-hidden py-24 md:py-28" style={{ backgroundImage: MOOD_BRONZE }}>
        <div className="mx-auto max-w-5xl px-6">
          <motion.div {...reveal(0)} className="text-center">
            <Eyebrow>La diferencia es legal</Eyebrow>
            <h2 className="mt-5 text-balance font-display text-3xl leading-tight tracking-tight md:text-5xl">
              ¿Cuál quieres ser tú?
            </h2>
          </motion.div>

          <div className="mt-14 grid gap-6 md:grid-cols-2">
            <motion.div
              {...reveal(0.05)}
              whileHover={{ y: -6, scale: 1.012 }}
              className="rounded-[1.75rem] p-8 ring-1 ring-gold/25"
              style={{ backgroundImage: MOOD_GOLD }}
            >
              <Flotante distancia={6} duracion={4.4}>
                <MascotaFeliz className="mx-auto h-28 w-28" />
              </Flotante>
              <h3 className="mt-1 text-center font-display text-2xl text-ink-fixed">Marca protegida</h3>
              <ul className="mt-5 space-y-3">
                {marcaProtegida.map((linea) => (
                  <li key={linea} className="flex gap-2.5 text-sm leading-relaxed text-ink-fixed/85">
                    <span className="mt-2 h-1.5 w-1.5 shrink-0 rounded-full bg-ink-fixed/60" />
                    {linea}
                  </li>
                ))}
              </ul>
            </motion.div>

            <motion.div
              {...reveal(0.12)}
              whileHover={{ y: -6, scale: 1.012 }}
              className="rounded-[1.75rem] p-8 ring-1 ring-red-400/20"
              style={{ backgroundImage: MOOD_RISK }}
            >
              <Flotante distancia={5} duracion={4.8} delay={0.3}>
                <MascotaPreocupada className="mx-auto h-28 w-28" />
              </Flotante>
              <h3 className="mt-1 text-center font-display text-2xl">Marca no protegida</h3>
              <ul className="mt-5 space-y-3">
                {marcaNoProtegida.map((linea) => (
                  <li key={linea} className="flex gap-2.5 text-sm leading-relaxed text-night-ink/65">
                    <span className="mt-2 h-1.5 w-1.5 shrink-0 rounded-full bg-red-300/40" />
                    {linea}
                  </li>
                ))}
              </ul>
            </motion.div>
          </div>
        </div>
      </section>

      {/* Por qué importa el símbolo ® */}
      <section ref={whyRef} className="relative overflow-hidden py-24 md:py-28" style={{ backgroundImage: MOOD_DEEP }}>
        <motion.div
          aria-hidden="true"
          style={{ y: whyGlyphY }}
          className="pointer-events-none absolute right-[-4%] top-1/2 -translate-y-1/2 select-none font-display text-[26rem] leading-none text-gold/[0.05]"
        >
          ®
        </motion.div>
        <div className="relative mx-auto max-w-2xl px-6">
          <motion.div {...reveal(0)}>
            <Eyebrow>El símbolo que importa</Eyebrow>
            <h2 className="mt-5 text-balance font-display text-3xl leading-tight tracking-tight md:text-5xl">
              ¿Por qué es tan importante registrar tu marca?
            </h2>
            <p className="mt-6 text-balance text-base leading-relaxed text-night-ink/70">
              ¿Has visto ese símbolo <span className="text-gold">®</span> al lado de algunas
              marcas? No está ahí solo por verse bien. Significa que esa marca ya está
              legalmente protegida ante la Superintendencia de Industria y Comercio.
              Tenerlo te da el derecho exclusivo de usar tu nombre o tu logo y, más
              importante aún, evita que otros lo copien o lo usen sin permiso.
            </p>
            <div className="mt-6 flex gap-3 border-l-2 border-gold/40 pl-4">
              <Quotes weight="fill" className="mt-1 h-5 w-5 shrink-0 text-gold/50" />
              <p className="font-display text-lg italic leading-snug text-night-ink/80">
                Esta marca es seria, es confiable y está bien hecha.
              </p>
            </div>
            <div className="mt-9">
              <CtaButton>Agenda tu consulta gratuita</CtaButton>
            </div>
          </motion.div>
        </div>
      </section>

      {/* CTA final: el sello entra con un rebote (spring), como si acabara
          de estampar el documento. */}
      <section className="relative overflow-hidden py-24 md:py-32" style={{ backgroundImage: MOOD_DEEP }}>
        <div
          aria-hidden="true"
          className="gradient-animate absolute inset-0"
          style={{
            backgroundImage:
              "radial-gradient(700px circle at 50% 30%, rgba(217,169,37,0.18), transparent 60%)",
          }}
        />
        <div className="relative mx-auto flex max-w-xl flex-col items-center px-6 text-center">
          <motion.span
            initial={{ opacity: 0, scale: 0.4, rotate: -25 }}
            whileInView={{ opacity: 1, scale: 1, rotate: 0 }}
            viewport={{ once: true, amount: 0.6 }}
            transition={{ type: "spring", stiffness: 260, damping: 16 }}
            className="flex h-16 w-16 items-center justify-center rounded-full text-ink-fixed"
            style={{ backgroundImage: MOOD_GOLD, boxShadow: "0 20px 40px -16px rgba(169,124,22,0.6)" }}
          >
            <Certificate weight="duotone" className="h-8 w-8" />
          </motion.span>
          <motion.h2
            {...reveal(0.08)}
            className="mt-7 text-balance font-display text-3xl leading-tight tracking-tight md:text-5xl"
          >
            ¿Listo para proteger tu marca?
          </motion.h2>
          <motion.p
            {...reveal(0.16)}
            className="mt-4 max-w-md text-balance text-base leading-relaxed text-night-ink/65"
          >
            Agenda una asesoría gratuita y te explicamos, sin enredos, todo lo que
            necesitas para que tu marca sea legalmente tuya.
          </motion.p>
          <motion.div {...reveal(0.24)} className="mt-9">
            <CtaButton>Solicita una consulta</CtaButton>
          </motion.div>
        </div>
      </section>
    </main>
  );
}
