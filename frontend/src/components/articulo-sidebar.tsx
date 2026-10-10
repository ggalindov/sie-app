import Link from "next/link";
import { ArrowRight, CalendarBlank, Clock, UserCircle, WhatsappLogo } from "@phosphor-icons/react/dist/ssr";
import { siteConfig } from "@/lib/site-config";

// Columna lateral del artículo (pedido explícito del usuario: "mas nivel articulo o periodico
// con dos columnas"), SOLO en escritorio (`hidden lg:block`, ver blog/[slug]/page.tsx) -- la
// versión móvil no se toca. El cuerpo del texto SÍ corre en columnas CSS a partir de lg (ver
// .contenido-articulo en globals.css, pedido explícito posterior del usuario anulando la
// decisión original de este comentario); este riel es el complemento: metadatos + llamada a
// la acción, agrandado a pedido explícito ("al lado mas grande quien lo redacto, fecha y si
// necesitas orientacion") -- más padding, tipografía más grande, pista de 380px en vez de
// 300px (ver el grid en blog/[slug]/page.tsx).
//
// Server Component a propósito (sin "use client"): es contenido estático por artículo, sin
// ninguna interacción que necesite JS -- añadir Motion aquí sería ruido, no elegancia.
export function ArticuloSidebar({
  categoria,
  autorNombre,
  fecha,
  tiempoLecturaMin,
}: {
  categoria: string;
  autorNombre: string;
  fecha: string;
  tiempoLecturaMin: number | null;
}) {
  return (
    <aside className="hidden lg:block" aria-label="Información del artículo">
      {/* sticky: se queda visible mientras se hace scroll por un artículo largo, en vez de
          desaparecer tras los primeros párrafos -- top-32 deja el mismo respiro que el resto
          de la página bajo el nav flotante. */}
      <div className="sticky top-32 space-y-6">
        <div className="rounded-3xl border border-line bg-surface p-8">
          <p className="text-xs font-semibold uppercase tracking-[0.12em] text-gold-deep">{categoria}</p>
          <dl className="mt-5 space-y-4 text-base text-ink-soft">
            <div className="flex items-center gap-3">
              <UserCircle weight="bold" className="h-5 w-5 shrink-0 text-ink-soft/70" />
              <span>{autorNombre}</span>
            </div>
            <div className="flex items-center gap-3">
              <CalendarBlank weight="bold" className="h-5 w-5 shrink-0 text-ink-soft/70" />
              <span>{fecha}</span>
            </div>
            {tiempoLecturaMin && (
              <div className="flex items-center gap-3">
                <Clock weight="bold" className="h-5 w-5 shrink-0 text-ink-soft/70" />
                <span>{tiempoLecturaMin} min de lectura</span>
              </div>
            )}
          </dl>
        </div>

        <div className="rounded-3xl bg-night p-8">
          <p className="font-display text-2xl leading-snug text-night-ink">¿Necesitas orientación sobre esto?</p>
          <p className="mt-3 text-base leading-relaxed text-night-ink/65">
            Agenda una asesoría personalizada con uno de nuestros abogados especialistas.
          </p>
          <Link
            href="/#contacto"
            className="mt-6 inline-flex items-center gap-2 rounded-full bg-gold px-6 py-3.5 text-base font-semibold text-ink-fixed transition-colors hover:bg-gold-deep"
          >
            {siteConfig.ctaPrincipal}
            <ArrowRight weight="bold" className="h-4 w-4" />
          </Link>
          <a
            href={siteConfig.whatsapp}
            target="_blank"
            rel="noreferrer"
            className="mt-5 flex items-center gap-2 text-base font-medium text-night-ink/75 transition-colors hover:text-night-ink"
          >
            <WhatsappLogo weight="bold" className="h-5 w-5" />
            Escríbenos por WhatsApp
          </a>
        </div>
      </div>
    </aside>
  );
}
