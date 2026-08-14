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
  const navAlpha = useTransform(scrollY, [0, 140], [0, 96]);
  const navBorder = useTransform(scrollY, [0, 140], [0, 1]);

  return (
    <>
      <header className="fixed inset-x-0 top-0 z-40">
        <motion.div
          style={{ "--nav-alpha": navAlpha, "--nav-border": navBorder } as CSSProperties}
          className="nav-bar mx-auto flex max-w-7xl items-center justify-between gap-4 px-6 py-4 backdrop-blur-xl md:px-10"
        >
          <Link
            href="/"
            className="flex shrink-0 items-center gap-2.5 py-1"
            onClick={() => setOpen(false)}
          >
            <Image
              src="/marca/logo.png"
              alt={siteConfig.nombre}
              width={30}
              height={30}
              className="h-[30px] w-[30px] object-contain"
              priority
            />
            <span className="font-display text-base font-semibold tracking-tight">
              {siteConfig.nombre}
            </span>
          </Link>

          <nav className="hidden items-center gap-1 md:flex">
            {navLinks.map((link) => (
              <NavLinkUnderline key={link.href} href={link.href} label={link.label} />
            ))}
          </nav>

          <div className="flex items-center gap-2">
            <ThemeToggle className="hidden text-ink-soft hover:bg-ink/5 hover:text-ink sm:flex" />

            <MagneticButton strength={0.35} className="hidden sm:inline-block">
              <Link
                href="#agendar"
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
      </header>

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
                href="#agendar"
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
