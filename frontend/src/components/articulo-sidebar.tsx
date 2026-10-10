import Link from "next/link";
import { ArrowRight, CalendarBlank, Clock, UserCircle, WhatsappLogo } from "@phosphor-icons/react/dist/ssr";
import { siteConfig } from "@/lib/site-config";

// Columna lateral del artículo (pedido explícito del usuario: "mas nivel articulo o periodico
// con dos columnas"), SOLO en escritorio (`hidden lg:block`, ver blog/[slug]/page.tsx) -- la
// versión móvil no se toca. Deliberadamente NO es el cuerpo del texto en columnas CSS
// (column-count): un periódico impreso usa eso por restricción de papel, pero en pantalla
// obliga a bajar y volver a subir para seguir leyendo, justo lo contrario de "sencillo de
// leer" que pidió el usuario. La convención real de los sitios de noticias/revistas (NYT,
// Medium, etc.) es columna de lectura + riel lateral con metadatos y una llamada a la acción,
// que es lo que se construye aquí.
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
      <div className="sticky top-32 space-y-5">
        <div className="rounded-2xl border border-line bg-surface p-6">
          <p className="text-[11px] font-semibold uppercase tracking-[0.1em] text-gold-deep">{categoria}</p>
          <dl className="mt-4 space-y-3 text-sm text-ink-soft">
            <div className="flex items-center gap-2.5">
              <UserCircle weight="bold" className="h-4 w-4 shrink-0 text-ink-soft/70" />
              <span>{autorNombre}</span>
            </div>
            <div className="flex items-center gap-2.5">
              <CalendarBlank weight="bold" className="h-4 w-4 shrink-0 text-ink-soft/70" />
              <span>{fecha}</span>
            </div>
            {tiempoLecturaMin && (
              <div className="flex items-center gap-2.5">
                <Clock weight="bold" className="h-4 w-4 shrink-0 text-ink-soft/70" />
                <span>{tiempoLecturaMin} min de lectura</span>
              </div>
            )}
          </dl>
        </div>

        <div className="rounded-2xl bg-night p-6">
          <p className="font-display text-lg leading-snug text-night-ink">¿Necesitas orientación sobre esto?</p>
          <p className="mt-2 text-sm leading-relaxed text-night-ink/65">
            Agenda una asesoría personalizada con uno de nuestros abogados especialistas.
          </p>
          <Link
            href="/#contacto"
            className="mt-5 inline-flex items-center gap-2 rounded-full bg-gold px-5 py-2.5 text-sm font-semibold text-ink-fixed transition-colors hover:bg-gold-deep"
          >
            {siteConfig.ctaPrincipal}
            <ArrowRight weight="bold" className="h-3.5 w-3.5" />
          </Link>
          <a
            href={siteConfig.whatsapp}
            target="_blank"
            rel="noreferrer"
            className="mt-4 flex items-center gap-2 text-sm font-medium text-night-ink/75 transition-colors hover:text-night-ink"
          >
            <WhatsappLogo weight="bold" className="h-4 w-4" />
            Escríbenos por WhatsApp
          </a>
        </div>
      </div>
    </aside>
  );
}
