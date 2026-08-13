"use client";

import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import { SiteNav } from "@/components/site-nav";
import { SiteFooter } from "@/components/site-footer";
import { WhatsappFloat } from "@/components/whatsapp-float";
import { ChatbotWidget } from "@/components/chatbot-widget";
import { ScrollProgress } from "@/components/scroll-progress";
import { ScrollAmbient } from "@/components/scroll-ambient";

// El panel administrativo (/admin/**) tiene su propio shell (sidebar/topbar) y no debe
// mostrar el nav, footer, WhatsApp flotante ni chatbot del sitio público.
export function SiteChrome({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const esAdmin = pathname?.startsWith("/admin");

  if (esAdmin) {
    return <>{children}</>;
  }

  return (
    <div className="site-theme relative flex min-h-full flex-1 flex-col bg-paper text-ink">
      <div className="ambient-field" aria-hidden="true" />
      <ScrollAmbient />
      <ScrollProgress />
      <SiteNav />
      {children}
      <SiteFooter />
      <WhatsappFloat />
      <ChatbotWidget />
    </div>
  );
}
