import type { Metadata } from "next";
import { Playfair_Display, Manrope } from "next/font/google";
import Script from "next/script";
import { MotionConfig } from "motion/react";
import { Toaster } from "sonner";
import { SiteChrome } from "@/components/site-chrome";
import { siteConfig } from "@/lib/site-config";
import "./globals.css";

// Misma derivación que sitemap.ts/robots.ts: el sitio público vive un nivel
// arriba de NEXT_PUBLIC_API_URL (que apunta a ".../api"), sin depender de
// una variable de entorno nueva solo para esto.
const SITE_URL = (process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:3000").replace(/\/api\/?$/, "");

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

const TITULO_BASE = "SIE Jurídicos: asesoría legal con más de 20 años de experiencia";
const DESCRIPCION_BASE =
  "Firma de abogados en Bogotá con más de 800 casos ganados. Derecho Laboral, Familia, Civil, Mercantil, Administrativo y Constitucional.";

export const metadata: Metadata = {
  metadataBase: new URL(SITE_URL),
  title: {
    default: TITULO_BASE,
    template: "%s | SIE Jurídicos",
  },
  description: DESCRIPCION_BASE,
  keywords: [
    "abogados Bogotá",
    "firma de abogados Colombia",
    "asesoría legal Bogotá",
    "derecho laboral Bogotá",
    "derecho de familia Colombia",
    "abogado civil Bogotá",
    "derecho mercantil Colombia",
    "derecho administrativo Bogotá",
    "derecho constitucional Colombia",
    "tutela Bogotá",
  ],
  alternates: {
    canonical: "/",
  },
  openGraph: {
    type: "website",
    locale: "es_CO",
    url: SITE_URL,
    siteName: "SIE Jurídicos",
    title: TITULO_BASE,
    description: DESCRIPCION_BASE,
  },
  twitter: {
    card: "summary_large_image",
    title: TITULO_BASE,
    description: DESCRIPCION_BASE,
  },
};

// LegalService (subtipo de LocalBusiness en schema.org): le da a Google datos
// estructurados verificables para mostrar resultados enriquecidos (nombre,
// teléfono, ciudad, redes sociales) en vez de solo un enlace azul genérico.
// Todos los datos vienen de site-config.ts -- nunca se inventa una dirección
// de calle que no tenemos confirmada (ver CLAUDE.md: no fabricar datos de la
// firma sin el texto real).
const jsonLd = {
  "@context": "https://schema.org",
  "@type": "LegalService",
  name: siteConfig.nombre,
  image: `${SITE_URL}/marca/logo.png`,
  url: SITE_URL,
  telephone: siteConfig.telefonoHref.replace("tel:", ""),
  address: {
    "@type": "PostalAddress",
    addressLocality: "Bogotá D.C.",
    addressCountry: "CO",
  },
  areaServed: "CO",
  sameAs: [siteConfig.redes.facebook, siteConfig.redes.instagram, siteConfig.redes.linkedin],
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
        <script
          type="application/ld+json"
          // eslint-disable-next-line react/no-danger -- JSON.stringify de un objeto propio, no de entrada de usuario
          dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }}
        />
        {/* Oscuro por defecto (pedido explícito): antes, sin preferencia guardada, se
            seguía el prefers-color-scheme del sistema operativo del visitante. Ahora el
            único caso que cae a claro es que la persona ya haya elegido "light" a mano
            con el interruptor de tema (queda en localStorage) -- ya no importa el tema
            del sistema operativo. */}
        <Script id="theme-init" strategy="beforeInteractive">
          {"(function(){try{var s=localStorage.getItem('sie-theme');var d=s!=='light';document.documentElement.setAttribute('data-theme', d?'dark':'light');}catch(e){}})();"}
        </Script>
        <MotionConfig reducedMotion="user">
          <SiteChrome>{children}</SiteChrome>
          <Toaster position="bottom-right" richColors />
        </MotionConfig>
      </body>
    </html>
  );
}
