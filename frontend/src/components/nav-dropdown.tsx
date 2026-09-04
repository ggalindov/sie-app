"use client";

import Image from "next/image";
import Link from "next/link";
import { useEffect, useRef, useState, type ReactNode } from "react";
import { AnimatePresence, motion } from "motion/react";
import {
  ArrowRight,
  Briefcase,
  Users,
  Scales,
  Buildings,
  Bank,
  ShieldCheck,
  Phone,
  EnvelopeSimple,
  MapPin,
  WhatsappLogo,
} from "@phosphor-icons/react";
import { areasPractica, equipo } from "@/lib/content";
import { siteConfig } from "@/lib/site-config";
import { getArticulos, type ArticuloResumen } from "@/lib/api";
import { NavLinkUnderline } from "@/components/nav-link-underline";

const EASE = [0.16, 1, 0.3, 1] as const;
// Cuánto espera antes de cerrar el panel al salir del trigger o del panel mismo: sin este
// margen, mover el mouse en diagonal del enlace hacia el panel (que empieza unos píxeles más
// abajo) lo cerraba antes de llegar -- el clásico problema de "hover intent" de cualquier
// menú desplegable.
const RETRASO_CIERRE_MS = 180;

const iconosAreas = [Briefcase, Users, Scales, Buildings, Bank, ShieldCheck];

const staggerContenedor = {
  oculto: {},
  visible: { transition: { staggerChildren: 0.04 } },
};
const staggerItem = {
  oculto: { opacity: 0, y: 8 },
  visible: { opacity: 1, y: 0, transition: { duration: 0.3, ease: EASE } },
};

function PanelLaFirma() {
  const stats = [
    { valor: "20+", etiqueta: "años de trayectoria" },
    { valor: "800+", etiqueta: "casos ganados" },
    { valor: "8", etiqueta: "profesionales" },
  ];
  return (
    <motion.div variants={staggerContenedor} initial="oculto" animate="visible" className="w-72 p-5">
      <motion.p variants={staggerItem} className="text-sm leading-relaxed text-ink-soft">
        Asesoría jurídica seria, transparente y comprometida con la justicia. Así empezamos
        hace más de 20 años, y así seguimos hoy.
      </motion.p>
      <motion.div variants={staggerItem} className="mt-4 grid grid-cols-3 gap-2 border-t border-line pt-4">
        {stats.map((s) => (
          <div key={s.etiqueta} className="text-center">
            <p className="font-display text-xl text-gold-deep">{s.valor}</p>
            <p className="mt-0.5 text-[10px] leading-tight text-ink-soft">{s.etiqueta}</p>
          </div>
        ))}
      </motion.div>
    </motion.div>
  );
}

function PanelAreas() {
  return (
    <motion.div
      variants={staggerContenedor}
      initial="oculto"
      animate="visible"
      className="w-72 p-2"
    >
      {areasPractica.map((area, i) => {
        const Icono = iconosAreas[i];
        return (
          <motion.div key={area.slug} variants={staggerItem}>
            <Link
              href={`/areas/${area.slug}`}
              className="group flex items-center gap-3 rounded-xl px-3 py-2.5 transition-colors hover:bg-gold/8"
            >
              <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-gold-pale/60 text-gold-deep transition-transform duration-300 group-hover:scale-110">
                <Icono weight="light" className="h-[15px] w-[15px]" />
              </span>
              <span className="min-w-0 flex-1 text-sm leading-snug text-ink transition-colors group-hover:text-gold-deep">
                {area.nombre}
              </span>
              <ArrowRight
                weight="bold"
                className="h-3 w-3 shrink-0 text-gold-deep opacity-0 transition-all duration-300 group-hover:translate-x-0.5 group-hover:opacity-100"
              />
            </Link>
          </motion.div>
        );
      })}
    </motion.div>
  );
}

function PanelEquipo() {
  const destacados = equipo.slice(0, 5);
  const restantes = equipo.length - destacados.length;
  return (
    <motion.div variants={staggerContenedor} initial="oculto" animate="visible" className="p-5">
      <motion.div variants={staggerItem} className="flex items-center gap-2.5">
        {destacados.map((m) => (
          <span
            key={m.nombre}
            title={m.nombre}
            className="relative h-11 w-11 shrink-0 overflow-hidden rounded-full ring-1 ring-line transition-transform duration-300 hover:z-10 hover:scale-110"
          >
            <Image
              src={m.foto}
              alt={m.nombre}
              fill
              sizes="44px"
              className="object-cover"
              style={{ objectPosition: m.posicion }}
            />
          </span>
        ))}
        {restantes > 0 && (
          <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-gold-pale/50 text-xs font-medium text-gold-deep ring-1 ring-line">
            +{restantes}
          </span>
        )}
      </motion.div>
      <motion.div variants={staggerItem}>
        <Link href="/#equipo" className="mt-4 flex items-center gap-1.5 text-sm font-medium text-gold-deep">
          Conoce a todo el equipo
          <ArrowRight weight="bold" className="h-3.5 w-3.5" />
        </Link>
      </motion.div>
    </motion.div>
  );
}

function PanelBlog() {
  const [articulo, setArticulo] = useState<ArticuloResumen | null | undefined>(undefined);

  useEffect(() => {
    let vigente = true;
    getArticulos()
      .then((res) => {
        if (vigente) setArticulo(res[0] ?? null);
      })
      .catch(() => {
        if (vigente) setArticulo(null);
      });
    return () => {
      vigente = false;
    };
  }, []);

  return (
    <motion.div variants={staggerContenedor} initial="oculto" animate="visible" className="w-64 p-3">
      {articulo === undefined ? (
        <div className="p-1">
          <div className="aspect-[16/10] animate-pulse rounded-xl bg-ink/8" />
          <div className="mt-3 h-3 w-3/4 animate-pulse rounded-full bg-ink/8" />
        </div>
      ) : articulo === null ? (
        <motion.p variants={staggerItem} className="p-2 text-sm text-ink-soft">
          Muy pronto encontrarás aquí contenido jurídico nuevo.
        </motion.p>
      ) : (
        <motion.div variants={staggerItem}>
          <Link href={`/blog/${articulo.slug}`} className="group block">
            <span className="relative flex aspect-[16/10] items-center justify-center overflow-hidden rounded-xl bg-night">
              {articulo.imagenUrl ? (
                // eslint-disable-next-line @next/next/no-img-element -- enlace externo arbitrario pegado por el admin, mismo motivo que blog-grid.tsx
                <img
                  src={articulo.imagenUrl}
                  alt={articulo.titulo}
                  className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
                />
              ) : (
                <span className="font-display text-lg text-gold/40">{articulo.categoria.nombre}</span>
              )}
            </span>
            <span className="mt-3 block text-xs font-medium uppercase tracking-[0.06em] text-gold-deep">
              {articulo.categoria.nombre}
            </span>
            <span className="mt-1 line-clamp-2 block text-sm font-medium leading-snug text-ink transition-colors group-hover:text-gold-deep">
              {articulo.titulo}
            </span>
          </Link>
        </motion.div>
      )}
      <motion.div variants={staggerItem}>
        <Link
          href="/blog"
          className="mt-2 flex items-center gap-1.5 border-t border-line p-3 pb-1 text-sm font-medium text-gold-deep"
        >
          Ver todos los artículos
          <ArrowRight weight="bold" className="h-3.5 w-3.5" />
        </Link>
      </motion.div>
    </motion.div>
  );
}

function PanelContacto() {
  const canales = [
    { icono: Phone, label: siteConfig.telefono, href: siteConfig.telefonoHref },
    { icono: WhatsappLogo, label: "Escríbenos por WhatsApp", href: siteConfig.whatsapp },
    { icono: EnvelopeSimple, label: siteConfig.correo, href: `mailto:${siteConfig.correo}` },
    { icono: MapPin, label: siteConfig.ciudad, href: "/#contacto" },
  ];
  return (
    <motion.div variants={staggerContenedor} initial="oculto" animate="visible" className="w-72 p-2">
      {canales.map((c) => (
        <motion.div key={c.label} variants={staggerItem}>
          <a
            href={c.href}
            target={c.href.startsWith("http") ? "_blank" : undefined}
            rel={c.href.startsWith("http") ? "noopener noreferrer" : undefined}
            className="group flex items-center gap-3 rounded-xl p-2.5 transition-colors hover:bg-gold/8"
          >
            <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-gold-pale/60 text-gold-deep transition-transform duration-300 group-hover:scale-110">
              <c.icono weight="light" className="h-4 w-4" />
            </span>
            <span className="text-sm text-ink transition-colors group-hover:text-gold-deep">{c.label}</span>
          </a>
        </motion.div>
      ))}
    </motion.div>
  );
}

// Cada enlace del nav que tiene contenido real detrás (áreas, equipo, artículos, canales)
// gana un panel propio -- pedido explícito del usuario. "Contacto" no tiene un ancla propia
// además del panel (el panel ES la acción, un clic directo a llamar/escribir), así que su
// href sigue siendo /#contacto para quien navega por teclado o sin hover.
const paneles: Record<string, () => ReactNode> = {
  "/#quienes-somos": () => <PanelLaFirma />,
  "/#areas": () => <PanelAreas />,
  "/#equipo": () => <PanelEquipo />,
  "/blog": () => <PanelBlog />,
  "/#contacto": () => <PanelContacto />,
};

// Cómo se alinea cada panel bajo su enlace: el primero y el último enlace del nav quedan
// cerca del borde del contenedor -- centrarlos los sacaría de la pantalla en viewports
// angostos. align se pasa por posición (i === 0 -> izquierda, último -> derecha, el resto
// centrado), no por nombre, para no acoplar este componente a los labels exactos del nav.
export function NavItemConPanel({
  href,
  label,
  align,
}: {
  href: string;
  label: string;
  align: "left" | "center" | "right";
}) {
  const [abierto, setAbierto] = useState(false);
  const cierreTimeout = useRef<ReturnType<typeof setTimeout> | null>(null);
  const Panel = paneles[href];

  function abrir() {
    if (cierreTimeout.current) clearTimeout(cierreTimeout.current);
    setAbierto(true);
  }
  function programarCierre() {
    cierreTimeout.current = setTimeout(() => setAbierto(false), RETRASO_CIERRE_MS);
  }
  useEffect(() => () => {
    if (cierreTimeout.current) clearTimeout(cierreTimeout.current);
  }, []);

  if (!Panel) {
    return <NavLinkUnderline href={href} label={label} />;
  }

  const alineacion =
    align === "left" ? "left-0" : align === "right" ? "right-0" : "left-1/2 -translate-x-1/2";
  const origen = align === "left" ? "top left" : align === "right" ? "top right" : "top center";

  return (
    <div
      className="relative"
      onMouseEnter={abrir}
      onMouseLeave={programarCierre}
      onFocus={abrir}
      onBlur={programarCierre}
    >
      <NavLinkUnderline href={href} label={label} />
      <AnimatePresence>
        {abierto && (
          <motion.div
            initial={{ opacity: 0, y: -8, scale: 0.97 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -8, scale: 0.97 }}
            transition={{ duration: 0.22, ease: EASE }}
            style={{ transformOrigin: origen }}
            className={`absolute top-full z-30 mt-3 overflow-hidden rounded-2xl bg-surface shadow-[0_30px_60px_-20px_rgba(20,19,15,0.4)] ring-1 ring-line ${alineacion}`}
          >
            {Panel()}
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
