"use client";

import Image from "next/image";
import Link from "next/link";
import { useState, type CSSProperties } from "react";
import { AnimatePresence, motion, useScroll, useTransform } from "motion/react";
import { List, X, WhatsappLogo } from "@phosphor-icons/react";
import { navLinks, siteConfig } from "@/lib/site-config";
import { MagneticButton } from "@/components/magnetic-button";
import { ThemeToggle } from "@/components/theme-toggle";
import { NavLinkUnderline } from "@/components/nav-link-underline";

const EASE = [0.16, 1, 0.3, 1] as const;

// La reducción de movimiento la maneja MotionConfig (reducedMotion="user") en el
// layout raíz para todos los componentes motion de la app.
export function SiteNav() {
  const [open, setOpen] = useState(false);
  const { scrollY } = useScroll();
  const navTop = useTransform(scrollY, [0, 140], [22, 10]);
  const navAlpha = useTransform(scrollY, [0, 140], [68, 94]);

  return (
    <>
      <motion.header
        style={{ top: navTop }}
        className="fixed inset-x-0 z-40 flex justify-center px-4"
      >
        <motion.div
          style={{ "--nav-alpha": navAlpha } as CSSProperties}
          className="nav-pill flex w-full max-w-3xl items-center justify-between gap-2 rounded-full border border-line py-2 pl-3 pr-2 shadow-[0_8px_30px_-12px_rgba(0,0,0,0.3)] backdrop-blur-xl"
        >
          <Link
            href="/"
            className="flex shrink-0 items-center gap-2 rounded-full py-1 pl-1 pr-2"
            onClick={() => setOpen(false)}
          >
            <Image
              src="/marca/logo.png"
              alt={siteConfig.nombre}
              width={32}
              height={32}
              className="h-8 w-8 object-contain"
              priority
            />
            <span className="hidden font-display text-base font-semibold tracking-tight sm:inline">
              {siteConfig.nombre}
            </span>
          </Link>

          <nav className="hidden items-center gap-1 md:flex">
            {navLinks.map((link) => (
              <NavLinkUnderline key={link.href} href={link.href} label={link.label} />
            ))}
          </nav>

          <div className="flex items-center gap-1">
            <ThemeToggle className="hidden text-ink-soft hover:bg-paper hover:text-ink sm:flex" />

            <MagneticButton strength={0.35} className="hidden sm:inline-block">
              <Link
                href="#contacto"
                className="group flex items-center gap-2 rounded-full bg-gold py-2.5 pl-5 pr-2 text-sm font-medium text-ink-fixed transition-transform duration-300 active:scale-[0.98]"
              >
                {siteConfig.ctaPrincipal}
                <span className="flex h-6 w-6 items-center justify-center rounded-full bg-ink-fixed/10 transition-transform duration-300 group-hover:translate-x-0.5">
                  <WhatsappLogo weight="fill" className="h-3.5 w-3.5" />
                </span>
              </Link>
            </MagneticButton>

            <button
              type="button"
              aria-label={open ? "Cerrar menú" : "Abrir menú"}
              aria-expanded={open}
              onClick={() => setOpen((v) => !v)}
              className="relative flex h-11 w-11 shrink-0 items-center justify-center rounded-full text-ink md:hidden"
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
        </motion.div>
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
                href="#contacto"
                onClick={() => setOpen(false)}
                className="inline-flex items-center gap-2 rounded-full bg-gold px-6 py-3 text-sm font-medium text-ink-fixed"
              >
                {siteConfig.ctaPrincipal}
              </Link>
              <ThemeToggle className="mt-6 text-night-ink/70 hover:bg-night-ink/10 hover:text-night-ink" />
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </>
  );
}
