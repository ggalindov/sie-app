import type { Metadata } from "next";
import { Playfair_Display, Manrope } from "next/font/google";
import Script from "next/script";
import { MotionConfig } from "motion/react";
import { Toaster } from "sonner";
import { SiteChrome } from "@/components/site-chrome";
import "./globals.css";

// De vuelta a la pareja original (Playfair Display + Manrope), a pedido explícito del
// usuario tras probar Fraunces, Bodoni Moda y Libre Caslon Display sin que ninguna
// convenciera. Identidad tipográfica base del proyecto.
const playfair = Playfair_Display({
  variable: "--font-display",
  subsets: ["latin"],
  style: ["normal", "italic"],
});

const manrope = Manrope({
  variable: "--font-sans",
  subsets: ["latin"],
});

export const metadata: Metadata = {
  title: {
    default: "SIE Jurídicos: asesoría legal con más de 20 años de experiencia",
    template: "%s | SIE Jurídicos",
  },
  description:
    "Firma de abogados en Bogotá con más de 800 casos ganados. Derecho Laboral, Familia, Civil, Mercantil, Administrativo y Constitucional.",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html
      lang="es"
      data-scroll-behavior="smooth"
      suppressHydrationWarning
      className={`${playfair.variable} ${manrope.variable} h-full antialiased`}
    >
      <body className="min-h-full flex flex-col bg-paper text-ink font-sans">
        <Script id="theme-init" strategy="beforeInteractive">
          {"(function(){try{var s=localStorage.getItem('sie-theme');var d=s?s==='dark':window.matchMedia('(prefers-color-scheme: dark)').matches;document.documentElement.setAttribute('data-theme', d?'dark':'light');}catch(e){}})();"}
        </Script>
        <MotionConfig reducedMotion="user">
          <SiteChrome>{children}</SiteChrome>
          <Toaster position="bottom-right" richColors />
        </MotionConfig>
      </body>
    </html>
  );
}
