import Link from "next/link";
import { ArrowRight, NewspaperClipping, BookOpenText } from "@phosphor-icons/react/dist/ssr";
import { getArticulos, type ArticuloResumen } from "@/lib/api";
import { BlogTeaserContenido } from "@/components/blog-teaser-contenido";

export async function BlogTeaser() {
  let articulos: ArticuloResumen[];
  try {
    articulos = (await getArticulos()).slice(0, 3);
  } catch {
    articulos = [];
  }

  return (
    <section id="blog" className="snap-slide section-seam relative py-12 md:py-16 lg:py-20 xl:py-24 flex flex-col justify-center">
      <div className="mx-auto flex w-full max-w-7xl xl:max-w-[1440px] 2xl:max-w-[1580px] flex-col justify-center px-6 lg:px-10 xl:px-12">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <h2 className="font-display text-3xl sm:text-4xl md:text-5xl lg:text-5xl xl:text-6xl leading-tight tracking-tight">
              Blog & Noticias: <span className="font-light italic text-ink/80">Criterio legal</span>
            </h2>
            <p className="mt-2 max-w-2xl text-xs sm:text-sm md:text-base xl:text-lg leading-relaxed text-ink-soft">
              Análisis claro de la ley colombiana, sentencias relevantes, precedentes y noticias jurídicas de impacto para personas y empresas.
            </p>
          </div>

          <Link
            href="/blog"
            className="group hidden shrink-0 items-center gap-2 rounded-xl border border-line bg-surface px-4 py-2 sm:px-5 sm:py-2.5 xl:px-6 xl:py-3 text-xs sm:text-sm xl:text-base font-medium text-ink shadow-xs transition-all duration-300 hover:border-gold-deep/50 hover:bg-gold/5 hover:text-gold-deep hover:shadow-sm sm:inline-flex"
          >
            <BookOpenText weight="duotone" className="h-4 w-4 xl:h-5 xl:w-5 text-gold-deep" />
            Ver todos los artículos y noticias
            <ArrowRight
              weight="bold"
              className="h-3.5 w-3.5 xl:h-4 xl:w-4 text-gold-deep transition-transform duration-300 group-hover:translate-x-1"
            />
          </Link>
        </div>

        <div className="mt-6 md:mt-8 xl:mt-10">
          <BlogTeaserContenido articulos={articulos} />
        </div>

        <div className="mt-6 text-center sm:hidden">
          <Link
            href="/blog"
            className="inline-flex items-center gap-2 text-xs sm:text-sm font-medium text-gold-deep underline decoration-gold/40 underline-offset-4"
          >
            <NewspaperClipping weight="duotone" className="h-4 w-4" />
            Ver todos los artículos y noticias
            <ArrowRight weight="bold" className="h-3.5 w-3.5" />
          </Link>
        </div>
      </div>
    </section>
  );
}
