import Link from "next/link";
import { ArrowRight, Compass } from "@phosphor-icons/react/dist/ssr";
import { siteConfig } from "@/lib/site-config";

// 404 a medida del sitio público: nunca la pantalla en blanco/gris genérica
// de Next.js. Sección simple (sin snap-slide, no forma parte del scroll de
// la home) que respeta la identidad "Tinta y Oro" y el tema claro/oscuro.
export default function NotFound() {
  return (
    <main className="flex flex-1 items-center justify-center px-6 py-32">
      <div className="mx-auto max-w-lg text-center">
        <span className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-gold-pale/60 text-gold-deep">
          <Compass weight="light" className="h-8 w-8" />
        </span>

        <p className="mt-8 font-display text-7xl leading-none tracking-tight text-ink md:text-8xl">
          404
        </p>
        <h1 className="mt-4 text-balance font-display text-2xl leading-snug tracking-tight text-ink md:text-3xl">
          Esta página no existe o fue movida.
        </h1>
        <p className="mt-4 text-balance text-base leading-relaxed text-ink-soft">
          Revisa el enlace que seguiste, o vuelve al inicio para encontrar lo
          que buscabas: áreas de práctica, el blog jurídico, o cómo agendar
          una asesoría con nuestro equipo.
        </p>

        <div className="mt-10 flex flex-wrap items-center justify-center gap-x-8 gap-y-4">
          <Link
            href="/"
            className="cta-boton group inline-flex items-center gap-3 rounded-lg bg-gold px-7 py-4 text-base font-medium text-ink-fixed active:scale-[0.98]"
          >
            Volver al inicio
            <ArrowRight
              weight="bold"
              className="h-4 w-4 shrink-0 transition-transform duration-300 group-hover:translate-x-1"
            />
          </Link>

          <a
            href={siteConfig.whatsapp}
            target="_blank"
            rel="noopener noreferrer"
            className="text-base font-medium text-ink underline decoration-line decoration-2 underline-offset-4 transition-colors duration-300 hover:decoration-gold"
          >
            Escríbenos por WhatsApp
          </a>
        </div>
      </div>
    </main>
  );
}
