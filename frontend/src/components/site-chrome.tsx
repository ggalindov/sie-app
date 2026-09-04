"use client";

import { usePathname } from "next/navigation";
import { useLayoutEffect, type ReactNode } from "react";
import { SiteNav } from "@/components/site-nav";
import { SiteFooter } from "@/components/site-footer";
import { WhatsappFloat } from "@/components/whatsapp-float";
import { ChatbotWidget } from "@/components/chatbot-widget";
import { ScrollProgress } from "@/components/scroll-progress";
import { ScrollAmbient } from "@/components/scroll-ambient";
import { ScrollSectionBlur } from "@/components/scroll-section-blur";
import { SlideNav } from "@/components/slide-nav";
import { NewsletterPopup } from "@/components/newsletter-popup";
import { VisitorTracker } from "@/components/visitor-tracker";
import { CuidaMarcaFloat } from "@/components/cuida-marca-float";
import { ConsultaCasoFloat } from "@/components/consulta-caso-float";
import { LoadingScreen } from "@/components/loading-screen";

// El panel administrativo (/admin/**) tiene su propio shell (sidebar/topbar) y no debe
// mostrar el nav, footer, WhatsApp flotante ni chatbot del sitio público.
export function SiteChrome({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const esAdmin = pathname?.startsWith("/admin");
  const esInicio = pathname === "/";
  // No tiene sentido ofrecer el acceso directo a "consulta tu caso" estando ya parado
  // en esa misma página.
  const esConsultaCaso = pathname === "/consulta-caso";

  // El scroll-snap tipo slide (ver globals.css, html.snap-inicio) solo tiene sentido en
  // la home, la única página con secciones .snap-slide de punta a punta. Sin este
  // interruptor, "mandatory" quedaba activo también en /blog, /consulta-caso, etc., y el
  // único punto de encaje que encontraba ahí (el footer) atrapaba el scroll.
  //
  // useLayoutEffect, no useEffect: bug real reportado por el usuario -- al navegar desde
  // la home hacia /areas/[slug] (u otra página sin .snap-slide), la página nueva abría
  // encajada hasta el pie de página en vez de arriba. useEffect corre DESPUÉS de que el
  // navegador ya pintó la página nueva; durante esa única pintada, snap-inicio seguía
  // presente en <html> con la página nueva ya visible, y el motor nativo de scroll-snap
  // "mandatory" del navegador la encajaba de inmediato en el único punto de anclaje que
  // encontraba ahí (.snap-footer, presente en todas las páginas). Quitar la clase un
  // instante después ya no deshace ese salto. useLayoutEffect corre de forma síncrona
  // antes de la pintada, así que la clase queda correcta desde el primer frame visible.
  useLayoutEffect(() => {
    document.documentElement.classList.toggle("snap-inicio", esInicio);
    return () => {
      document.documentElement.classList.remove("snap-inicio");
    };
  }, [esInicio]);

  if (esAdmin) {
    return <>{children}</>;
  }

  return (
    <>
      {/* SiteChrome no se desmonta entre navegaciones internas (solo cambia
          "children"), así que LoadingScreen -- montado una sola vez aquí --
          solo aparece en la carga real inicial del sitio, nunca al navegar
          entre secciones o páginas ya dentro de la sesión. */}
      <LoadingScreen />

      {/* fondo ambiental como hermano de nivel superior, no anidado dentro
          del wrapper de contenido: un z-index negativo dentro de otro
          contexto de apilamiento (position:relative) no se pintaba de forma
          fiable en algunos navegadores, dejando el fondo plano y sólido */}
      <div className="site-theme ambient-field" aria-hidden="true" />
      <div className="site-theme relative flex min-h-full flex-1 flex-col text-ink">
        <VisitorTracker />
        <ScrollAmbient />
        <ScrollProgress />
        {esInicio && <ScrollSectionBlur />}
        <SiteNav />
        {children}
        <SiteFooter />
        <SlideNav />
        {esInicio && <CuidaMarcaFloat />}
        {!esConsultaCaso && <ConsultaCasoFloat />}
        <WhatsappFloat />
        <ChatbotWidget />
        <NewsletterPopup />
      </div>
    </>
  );
}
