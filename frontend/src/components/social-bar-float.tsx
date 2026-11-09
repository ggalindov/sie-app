"use client";

import {
  FacebookLogo,
  InstagramLogo,
  LinkedinLogo,
  WhatsappLogo,
  YoutubeLogo,
} from "@phosphor-icons/react";
import { siteConfig } from "@/lib/site-config";

// Barra lateral fija en el borde izquierdo con accesos oficiales a redes sociales.
// Paleta suave y elegante alineada a la identidad "Tinta y Oro" de SIE Jurídicos.
const redes = [
  {
    nombre: "Facebook",
    href: siteConfig.redes.facebook,
    icono: FacebookLogo,
    colorBox: "bg-[#1c2433]/90 text-[#a3b8d8] hover:bg-[#2a3c5d] hover:text-white",
    aria: "Facebook de SIE Jurídicos",
  },
  {
    nombre: "LinkedIn",
    href: siteConfig.redes.linkedin,
    icono: LinkedinLogo,
    colorBox: "bg-[#162733]/90 text-[#9dc2dd] hover:bg-[#1e4563] hover:text-white",
    aria: "LinkedIn de SIE Jurídicos",
  },
  {
    nombre: "Instagram",
    href: siteConfig.redes.instagram,
    icono: InstagramLogo,
    colorBox: "bg-[#2d1b28]/90 text-[#dfaccf] hover:bg-[#522543] hover:text-white",
    aria: "Instagram de SIE Jurídicos",
  },
  {
    nombre: "YouTube",
    href: siteConfig.redes.youtube,
    icono: YoutubeLogo,
    colorBox: "bg-[#2d191a]/90 text-[#e5a6a8] hover:bg-[#5a2123] hover:text-white",
    aria: "Canal de YouTube de SIE Jurídicos",
  },
  {
    nombre: "WhatsApp",
    href: siteConfig.whatsapp,
    icono: WhatsappLogo,
    colorBox: "bg-[#172b22]/90 text-[#a3d8be] hover:bg-[#23583f] hover:text-white",
    aria: "WhatsApp Oficial de SIE Jurídicos",
  },
];

// Pedido explícito del usuario (2026-11-09): en /consulta-caso, en móvil, esta barra queda
// encima de la tarjeta de búsqueda y del contenido (no hay suficiente ancho para dejarle un
// carril propio a la izquierda sin tapar nada) -- se oculta solo ahí y solo en pantallas
// angostas (sm: la recupera), nunca en el resto del sitio ni en escritorio.
export function SocialBarFloat({ ocultarEnMovil = false }: { ocultarEnMovil?: boolean }) {
  return (
    <aside
      aria-label="Redes sociales oficiales"
      className={`fixed left-0 top-1/2 z-40 -translate-y-1/2 ${ocultarEnMovil ? "hidden sm:flex" : "flex"} flex-col rounded-r-xl overflow-hidden border-r border-y border-gold-deep/30 bg-night/95 backdrop-blur-md shadow-2xl divide-y divide-gold-deep/15 select-none`}
    >
      {redes.map((r, i) => {
        const Icono = r.icono;
        const esPrimero = i === 0;
        const esUltimo = i === redes.length - 1;
        return (
          <a
            key={r.nombre}
            href={r.href}
            target="_blank"
            rel="noopener noreferrer"
            aria-label={r.aria}
            title={r.nombre}
            className={`group relative flex h-10 w-10 sm:h-11 sm:w-11 items-center justify-center transition-all duration-300 hover:translate-x-1.5 ${r.colorBox} ${
              esPrimero ? "rounded-tr-xl" : ""
            } ${esUltimo ? "rounded-br-xl" : ""}`}
          >
            <Icono weight="bold" className="h-5 w-5 transition-transform duration-200 group-hover:scale-110" />

            {/* Tooltip hacia la derecha en paleta Tinta y Oro */}
            <span className="pointer-events-none absolute left-full ml-2.5 hidden whitespace-nowrap rounded-md border border-gold-deep/40 bg-night/95 px-2.5 py-1 text-xs font-semibold text-gold-pale shadow-xl backdrop-blur-sm transition-opacity group-hover:block z-50">
              {r.nombre}
            </span>
          </a>
        );
      })}
    </aside>
  );
}
