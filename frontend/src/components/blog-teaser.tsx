import Link from "next/link";
import { getArticulos, type ArticuloResumen } from "@/lib/api";
import { BlogTeaserLista, BlogTeaserVacio } from "@/components/blog-teaser-contenido";

// La suscripción al boletín ya no vive aquí: solo se ofrece desde el popup
// (NewsletterPopup, montado en SiteChrome). En desktop la sección tiene
// altura fija (100dvh, igual que Quienes Somos) para que el encaje del
// scroll-snap sea siempre exacto: si los artículos no caben enteros, se
// desplazan con su propio scroll interno en vez de estirar la sección.
export async function BlogTeaser() {
  let articulos: ArticuloResumen[];
  try {
    articulos = (await getArticulos()).slice(0, 3);
  } catch {
    articulos = [];
  }

  if (articulos.length === 0) {
    return (
      <section id="blog" className="snap-slide section-seam frame-fixed py-20">
        <div className="mx-auto flex max-w-7xl flex-col px-6 md:h-full md:justify-center md:py-16">
          <h2 className="shrink-0 font-display text-4xl leading-tight tracking-tight md:text-5xl">
            Blog y Noticias
          </h2>

          <div className="md:min-h-0 md:flex-1 md:overflow-y-auto md:pr-2">
            <BlogTeaserVacio />
          </div>
        </div>
      </section>
    );
  }

  const [destacado, ...resto] = articulos;

  return (
    <section id="blog" className="snap-slide section-seam frame-fixed py-20">
      <div className="mx-auto flex max-w-7xl flex-col px-6 md:h-full md:justify-center md:py-16">
        <div className="flex shrink-0 items-end justify-between gap-4">
          <h2 className="font-display text-4xl leading-tight tracking-tight md:text-5xl">
            Blog y Noticias
          </h2>
          <Link
            href="/blog"
            className="hidden shrink-0 items-center gap-1 text-sm font-medium text-ink underline decoration-line decoration-2 underline-offset-4 hover:decoration-gold sm:flex"
          >
            Ver todos los artículos
          </Link>
        </div>

        <div className="md:min-h-0 md:flex-1 md:overflow-y-auto md:pr-2">
          <BlogTeaserLista destacado={destacado} resto={resto} />

          <Link
            href="/blog"
            className="mt-6 flex items-center gap-1 text-sm font-medium text-ink underline decoration-line decoration-2 underline-offset-4 hover:decoration-gold sm:hidden"
          >
            Ver todos los artículos
          </Link>
        </div>
      </div>
    </section>
  );
}
