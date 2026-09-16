import Link from "next/link";
import Image from "next/image";
import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { ArrowLeft, ArrowRight, WhatsappLogo, CheckCircle, Scales } from "@phosphor-icons/react/dist/ssr";
import { areasPractica } from "@/lib/content";
import { siteConfig } from "@/lib/site-config";

export function generateStaticParams() {
  return areasPractica.map((area) => ({ slug: area.slug }));
}

export async function generateMetadata({
  params,
}: PageProps<"/areas/[slug]">): Promise<Metadata> {
  const { slug } = await params;
  const area = areasPractica.find((a) => a.slug === slug);
  if (!area) return {};
  return { title: area.nombre, description: area.resumen };
}

export default async function AreaPage({ params }: PageProps<"/areas/[slug]">) {
  const { slug } = await params;
  const area = areasPractica.find((a) => a.slug === slug);
  if (!area) notFound();

  const whatsappUrl = `https://wa.me/573243668845?text=${encodeURIComponent(area.whatsappMensaje)}`;
  const otras = areasPractica.filter((a) => a.slug !== area.slug);

  return (
    <main className="flex-1 pt-32 pb-24 md:pt-36">
      <div className="mx-auto max-w-4xl px-6">
        <Link
          href="/#areas"
          className="inline-flex items-center gap-2 text-sm font-medium text-ink-soft transition-colors hover:text-ink"
        >
          <ArrowLeft weight="bold" className="h-4 w-4" />
          Áreas de práctica
        </Link>

        {/* Portada Fotográfica */}
        <div className="relative mt-8 h-64 sm:h-80 md:h-96 w-full overflow-hidden rounded-3xl bg-night shadow-lg">
          <Image
            src={area.foto}
            alt={area.nombre}
            fill
            priority
            sizes="(max-width: 768px) 100vw, 900px"
            className="object-cover filter brightness-[0.88]"
          />
          <div className="absolute inset-0 bg-gradient-to-t from-night/90 via-night/40 to-transparent" />
          <div className="absolute bottom-6 inset-x-6 sm:inset-x-8">
            <span className="inline-block rounded-full bg-gold/20 backdrop-blur-md border border-gold/40 px-3 py-1 text-xs font-mono font-semibold text-gold tracking-wider">
              Especialidad Jurídica
            </span>
            <h1 className="mt-2 text-balance font-display text-3xl sm:text-4xl md:text-5xl font-bold leading-tight tracking-tight text-white">
              {area.nombre}
            </h1>
          </div>
        </div>

        <p className="mt-8 text-xl leading-relaxed text-ink/90 border-l-3 border-gold pl-4 font-medium">
          {area.resumen}
        </p>

        {/* Casos de Referencia */}
        <div className="mt-8 rounded-2xl bg-surface p-6 border border-line shadow-xs">
          <div className="flex items-center gap-2 mb-4">
            <Scales weight="bold" className="h-5 w-5 text-gold" />
            <h2 className="text-sm font-bold uppercase tracking-[0.14em] text-gold-deep">
              Casos de referencia y situaciones que atendemos
            </h2>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            {area.casosReferencia.map((caso, idx) => (
              <div
                key={idx}
                className="flex items-start gap-2.5 rounded-xl bg-paper/70 p-3 border border-line/60"
              >
                <CheckCircle weight="fill" className="h-4 w-4 text-gold shrink-0 mt-0.5" />
                <span className="text-sm font-medium text-ink leading-snug">
                  {caso}
                </span>
              </div>
            ))}
          </div>
        </div>

        <div className="mt-8 space-y-4 text-base leading-relaxed text-ink-soft">
          {area.descripcion.map((parrafo, i) => (
            <p key={i}>{parrafo}</p>
          ))}
        </div>

        <div className="mt-10 flex flex-wrap gap-4">
          <a
            href={whatsappUrl}
            target="_blank"
            rel="noopener noreferrer"
            // texto en tinta oscura, no blanca: blanco sobre este verde da ~2:1 de
            // contraste (falla WCAG AA); con tinta oscura sube a ~8:1.
            className="cta-boton group inline-flex items-center gap-3 rounded-lg bg-[#25D366] px-6 py-3.5 text-sm font-medium text-ink transition-transform duration-300 active:scale-[0.98]"
          >
            Escríbenos por WhatsApp
            <WhatsappLogo
              weight="fill"
              className="h-4 w-4 shrink-0 transition-transform duration-300 group-hover:translate-x-1"
            />
          </a>
          <Link
            href="/#agendar"
            className="cta-boton group inline-flex items-center gap-3 rounded-lg bg-gold px-6 py-3.5 text-sm font-medium text-ink transition-transform duration-300 active:scale-[0.98]"
          >
            {siteConfig.ctaPrincipal}
            <ArrowRight
              weight="bold"
              className="h-4 w-4 shrink-0 transition-transform duration-300 group-hover:translate-x-1"
            />
          </Link>
        </div>

        <div className="mt-20 border-t border-line pt-10">
          <p className="text-xs font-medium uppercase tracking-[0.14em] text-ink-soft">
            Otras áreas de práctica
          </p>
          <div className="mt-5 flex flex-wrap gap-3">
            {otras.map((otra) => (
              <Link
                key={otra.slug}
                href={`/areas/${otra.slug}`}
                className="rounded-full bg-ink/5 px-4 py-2 text-sm text-ink transition-colors hover:bg-gold-pale/50"
              >
                {otra.nombre}
              </Link>
            ))}
          </div>
        </div>
      </div>
    </main>
  );
}
