"use client";

import Image from "next/image";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useRef, useState, type CSSProperties } from "react";
import { AnimatePresence, motion, useScroll, useTransform } from "motion/react";
import { List, X, WhatsappLogo, Lock } from "@phosphor-icons/react";
import { navLinks, siteConfig } from "@/lib/site-config";
import { MagneticButton } from "@/components/magnetic-button";
import { NavItemConPanel } from "@/components/nav-dropdown";
import { HelpMenu } from "@/components/help-menu";

const EASE = [0.16, 1, 0.3, 1] as const;

// La reducción de movimiento la maneja MotionConfig (reducedMotion="user") en el
// layout raíz para todos los componentes motion de la app.
export function SiteNav() {
  const [open, setOpen] = useState(false);
  const [logoIluminado, setLogoIluminado] = useState(false);
  const pathname = usePathname();
  // Solo la home tiene un Hero de video oscuro de borde a borde detrás del nav, así
  // que solo ahí el texto puede nacer claro (--color-night-ink) y cruzar a oscuro al
  // hacer scroll. En cualquier otra página (p. ej. /blog) no hay ese video: el fondo
  // en scroll 0 ya sigue el tema activo (claro u oscuro), así que el texto debe usar
  // directamente el color de tinta del tema desde el inicio. Sin este corte, en modo
  // claro fuera de la home el nav nacía en --color-night-ink (un crema casi idéntico
  // al fondo claro de la página) y quedaba invisible/"pegado" hasta pasar 140px de
  // scroll.
  const esInicio = pathname === "/";
  const { scrollY } = useScroll();
  const navAlpha = useTransform(scrollY, [0, 140], [0, 96]);
  const navBorder = useTransform(scrollY, [0, 140], [0, 1]);
  const navInkScroll = useTransform(scrollY, [0, 140], [0, 100]);
  const headerRef = useRef<HTMLElement>(null);

  // Mide la altura real del nav (en vez de asumir un número fijo en rem) y
  // la publica como --nav-height en <html>: el "encaje" del scroll-snap
  // (.snap-slide usa esta variable en scroll-margin-top) queda siempre
  // exacto, sin importar cuánto cambie el logo o el contenido del nav.
  useEffect(() => {
    const el = headerRef.current;
    if (!el) return;
    const actualizar = () => {
      document.documentElement.style.setProperty("--nav-height", `${el.offsetHeight}px`);
    };
    actualizar();
    const observer = new ResizeObserver(actualizar);
    observer.observe(el);
    return () => observer.disconnect();
  }, []);

  // El menú móvil es un overlay a pantalla completa sin <dialog>/base-ui detrás (a
  // diferencia de los modales del sitio, que ya cierran con Escape de fábrica): sin este
  // listener, la única forma de cerrarlo era encontrar el botón X con el mouse.
  useEffect(() => {
    if (!open) return;
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape") setOpen(false);
    }
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [open]);

  return (
    <>
      <motion.header
        ref={headerRef}
        style={{ "--nav-alpha": navAlpha, "--nav-border": navBorder, "--nav-ink": esInicio ? navInkScroll : 100 } as CSSProperties}
        className="nav-bar fixed inset-x-0 top-0 z-40 backdrop-blur-xl"
      >
        <div className="mx-auto flex max-w-7xl items-center justify-between gap-4 px-6 py-4 md:px-10">
          <Link
            href="/"
            className="relative flex shrink-0 items-center py-1"
            onClick={() => setOpen(false)}
            onMouseEnter={() => setLogoIluminado(true)}
            onMouseLeave={() => setLogoIluminado(false)}
          >
            {/* Iluminación, no giro (pedido explícito del usuario): en reposo el halo dorado
                apenas respira (pulso lento e infinito); al pasar el mouse, el halo crece y el
                propio logo se ilumina con un resplandor cálido -- nunca rota, el sello dorado
                se queda quieto, solo cobra luz. El estado de hover vive en React (no
                whileHover de Motion) porque el halo es pointer-events-none -- nunca podría
                recibir su propio evento de hover, así que ambas capas leen el mismo estado
                del <Link> que las contiene. */}
            <motion.span
              aria-hidden="true"
              className="pointer-events-none absolute inset-0 -z-10 rounded-full bg-gold/30 blur-lg"
              animate={
                logoIluminado
                  ? { opacity: 0.65, scale: 1.4 }
                  : { opacity: [0.15, 0.35, 0.15], scale: 1 }
              }
              transition={
                logoIluminado
                  ? { duration: 0.5, ease: EASE }
                  : { duration: 3.2, repeat: Infinity, ease: "easeInOut" }
              }
            />
            <motion.div
              animate={{
                scale: logoIluminado ? 1.06 : 1,
                filter: logoIluminado
                  ? "brightness(1.18) drop-shadow(0 0 14px rgba(217,169,37,0.6))"
                  : "brightness(1) drop-shadow(0 0 0px rgba(217,169,37,0))",
              }}
              transition={{ duration: 0.45, ease: EASE }}
            >
              <Image
                src="/marca/logo.png"
                alt={siteConfig.nombre}
                width={52}
                height={45}
                className="h-11 w-auto object-contain md:h-12"
                priority
              />
            </motion.div>
          </Link>

          <nav className="hidden items-center gap-1 md:flex">
            {navLinks.map((link, i) => (
              <NavItemConPanel
                key={link.href}
                href={link.href}
                label={link.label}
                align={i === 0 ? "left" : i === navLinks.length - 1 ? "right" : "center"}
              />
            ))}
          </nav>

          <div className="flex items-center gap-2">
            <HelpMenu className="nav-text-crossfade hidden hover:bg-ink/5 sm:flex" />

            <MagneticButton strength={0.35} className="hidden sm:inline-block">
              <Link
                href="/#agendar"
                className="cta-boton group flex items-center gap-2 rounded-lg bg-gold py-2.5 pl-5 pr-4 text-sm font-medium text-ink-fixed active:scale-[0.98]"
              >
                {siteConfig.ctaPrincipal}
                <WhatsappLogo
                  weight="fill"
                  className="h-4 w-4 shrink-0 transition-transform duration-300 group-hover:translate-x-0.5"
                />
              </Link>
            </MagneticButton>

            {/* Deliberadamente pequeño y casi invisible en reposo (opacity-20): es la
                puerta de entrada del equipo interno, no una invitación pública -- solo
                quien ya sabe que está ahí lo va a notar. Sube a opacity-100 al pasar el
                mouse para que siga siendo encontrable, no un secreto imposible. */}
            <Link
              href="/admin/login"
              aria-label="Acceso interno para administradores y abogados"
              title="Acceso interno"
              className="nav-text-crossfade ml-3 hidden h-7 w-7 shrink-0 items-center justify-center rounded-full opacity-20 transition-opacity duration-300 hover:bg-ink/5 hover:opacity-100 sm:flex"
            >
              <Lock className="h-[11px] w-[11px]" weight="light" />
            </Link>

            <button
              type="button"
              aria-label={open ? "Cerrar menú" : "Abrir menú"}
              aria-expanded={open}
              onClick={() => setOpen((v) => !v)}
              className="nav-text-crossfade relative flex h-11 w-11 shrink-0 items-center justify-center rounded-full md:hidden"
            >
              <AnimatePresence initial={false} mode="wait">
                {open ? (
                  <motion.span
                    key="x"
                    initial={{ rotate: -90, opacity: 0 }}
                    animate={{ rotate: 0, opacity: 1 }}
                    exit={{ rotate: 90, opacity: 0 }}
                    transition={{ duration: 0.25, ease: EASE }}
                  >
                    <X className="h-6 w-6" weight="light" />
                  </motion.span>
                ) : (
                  <motion.span
                    key="list"
                    initial={{ rotate: 90, opacity: 0 }}
                    animate={{ rotate: 0, opacity: 1 }}
                    exit={{ rotate: -90, opacity: 0 }}
                    transition={{ duration: 0.25, ease: EASE }}
                  >
                    <List className="h-6 w-6" weight="light" />
                  </motion.span>
                )}
              </AnimatePresence>
            </button>
          </div>
        </div>
      </motion.header>

      <AnimatePresence>
        {open && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.3, ease: EASE }}
            className="fixed inset-0 z-30 flex flex-col justify-center bg-night/95 px-8 backdrop-blur-2xl md:hidden"
          >
            <nav className="flex flex-col gap-2">
              {navLinks.map((link, i) => (
                <motion.div
                  key={link.href}
                  initial={{ opacity: 0, y: 24 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ duration: 0.5, delay: 0.08 * i, ease: EASE }}
                >
                  <Link
                    href={link.href}
                    onClick={() => setOpen(false)}
                    className="font-display text-4xl text-night-ink"
                  >
                    {link.label}
                  </Link>
                </motion.div>
              ))}
            </nav>
            <motion.div
              initial={{ opacity: 0, y: 24 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.5, delay: 0.08 * navLinks.length, ease: EASE }}
              className="mt-10"
            >
              <Link
                href="/#agendar"
                onClick={() => setOpen(false)}
                className="cta-boton inline-flex items-center gap-2 rounded-lg bg-gold px-6 py-3 text-sm font-medium text-ink-fixed"
              >
                {siteConfig.ctaPrincipal}
              </Link>
              <div className="mt-8 flex items-center gap-4">
                <HelpMenu
                  align="left"
                  direction="up"
                  size={44}
                  onNavigate={() => setOpen(false)}
                  className="text-night-ink/80 hover:bg-night-ink/10 hover:text-night-ink"
                />
                <Link
                  href="/admin/login"
                  onClick={() => setOpen(false)}
                  aria-label="Acceso interno para administradores y abogados"
                  title="Acceso interno"
                  className="ml-2 flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-night-ink/30 transition-colors hover:bg-night-ink/10 hover:text-night-ink"
                >
                  <Lock className="h-[11px] w-[11px]" weight="light" />
                </Link>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </>
  );
}
