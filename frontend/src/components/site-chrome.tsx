"use client";

import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import { SiteNav } from "@/components/site-nav";
import { SiteFooter } from "@/components/site-footer";
import { WhatsappFloat } from "@/components/whatsapp-float";
import { ChatbotWidget } from "@/components/chatbot-widget";
import { ScrollProgress } from "@/components/scroll-progress";
import { ScrollAmbient } from "@/components/scroll-ambient";
import { SlideNav } from "@/components/slide-nav";

// El panel administrativo (/admin/**) tiene su propio shell (sidebar/topbar) y no debe
// mostrar el nav, footer, WhatsApp flotante ni chatbot del sitio público.
export function SiteChrome({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const esAdmin = pathname?.startsWith("/admin");

  if (esAdmin) {
    return <>{children}</>;
  }

  return (
    <>
      {/* fondo ambiental como hermano de nivel superior, no anidado dentro
          del wrapper de contenido: un z-index negativo dentro de otro
          contexto de apilamiento (position:relative) no se pintaba de forma
          fiable en algunos navegadores, dejando el fondo plano y sólido */}
      <div className="site-theme ambient-field" aria-hidden="true" />
      <div className="site-theme relative flex min-h-full flex-1 flex-col text-ink">
        <ScrollAmbient />
        <ScrollProgress />
        <SiteNav />
        {children}
        <SiteFooter />
        <SlideNav />
        <WhatsappFloat />
        <ChatbotWidget />
      </div>
    </>
  );
}
