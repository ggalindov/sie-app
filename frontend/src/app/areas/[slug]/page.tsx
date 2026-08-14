import Link from "next/link";
import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { ArrowLeft, ArrowRight, WhatsappLogo } from "@phosphor-icons/react/dist/ssr";
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
      <div className="mx-auto max-w-3xl px-6">
        <Link
          href="/#areas"
          className="inline-flex items-center gap-2 text-sm font-medium text-ink-soft transition-colors hover:text-ink"
        >
          <ArrowLeft weight="bold" className="h-4 w-4" />
          Áreas de práctica
        </Link>

        <h1 className="mt-8 text-balance font-display text-4xl leading-tight tracking-tight md:text-5xl">
          {area.nombre}
        </h1>
        <p className="mt-5 max-w-xl text-lg leading-relaxed text-ink-soft">
          {area.resumen}
        </p>

        <div className="mt-10 space-y-4 text-base leading-relaxed text-ink-soft">
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
            className="group inline-flex items-center gap-3 rounded-full bg-[#25D366] py-3.5 pl-7 pr-3 text-sm font-medium text-ink transition-transform duration-300 active:scale-[0.98]"
          >
            Escríbenos por WhatsApp
            <span className="flex h-8 w-8 items-center justify-center rounded-full bg-ink/10 transition-transform duration-300 group-hover:translate-x-0.5">
              <WhatsappLogo weight="fill" className="h-4 w-4" />
            </span>
          </a>
          <Link
            href="/#agendar"
            className="group inline-flex items-center gap-3 rounded-full bg-gold py-3.5 pl-7 pr-3 text-sm font-medium text-ink transition-transform duration-300 active:scale-[0.98]"
          >
            {siteConfig.ctaPrincipal}
            <span className="flex h-8 w-8 items-center justify-center rounded-full bg-ink/10 transition-transform duration-300 group-hover:translate-x-0.5">
              <ArrowRight className="h-4 w-4" weight="bold" />
            </span>
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
