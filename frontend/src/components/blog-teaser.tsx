import Link from "next/link";
import { ArrowUpRight } from "@phosphor-icons/react/dist/ssr";
import { getArticulos, type ArticuloResumen } from "@/lib/api";

function formatearFecha(iso: string) {
  return new Date(iso).toLocaleDateString("es-CO", {
    day: "numeric",
    month: "long",
    year: "numeric",
  });
}

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
            <div
              className="mt-10 flex min-h-[220px] flex-col items-center justify-center rounded-3xl bg-surface p-10 text-center ring-1 ring-line"
              style={{
                backgroundImage:
                  "radial-gradient(60% 80% at 50% 0%, rgba(217,169,37,0.1), transparent 70%)",
              }}
            >
              <p className="font-display text-xl">Estamos preparando nuestros primeros artículos.</p>
              <p className="mt-2 max-w-sm text-sm leading-relaxed text-ink-soft">
                Muy pronto encontrarás aquí contenido jurídico útil sobre las áreas en las
                que te podemos ayudar.
              </p>
            </div>
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
          <div className="mt-10 grid gap-7 md:grid-cols-2">
            <Link
              href={`/blog/${destacado.slug}`}
              className="group flex flex-col overflow-hidden rounded-3xl bg-surface ring-1 ring-line transition-colors md:row-span-2"
            >
              <div className="flex aspect-[16/9] items-center justify-center overflow-hidden bg-night md:aspect-auto md:min-h-[240px] md:flex-1">
                {destacado.imagenUrl ? (
                  // eslint-disable-next-line @next/next/no-img-element -- enlace externo arbitrario pegado por el admin
                  <img
                    src={destacado.imagenUrl}
                    alt={destacado.titulo}
                    className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
                  />
                ) : (
                  <span className="font-display text-4xl text-gold/40">
                    {destacado.categoria.nombre}
                  </span>
                )}
              </div>
              <div className="p-8">
                <p className="flex items-center gap-2 text-xs font-medium uppercase tracking-[0.1em] text-gold-deep">
                  {destacado.categoria.nombre}
                  {destacado.tipoContenido === "NOTICIA" && (
                    <span className="rounded-full bg-gold/10 px-2 py-0.5 text-[10px] text-gold-deep">
                      Noticia
                    </span>
                  )}
                </p>
                <h3 className="mt-3 font-display text-2xl leading-snug md:text-3xl">
                  {destacado.titulo}
                </h3>
                <p className="mt-3 text-sm leading-relaxed text-ink-soft">
                  {destacado.resumen}
                </p>
                <p className="mt-5 text-xs text-ink-soft">
                  {formatearFecha(destacado.fechaPublicacion)}
                  {destacado.tiempoLecturaMin && ` · ${destacado.tiempoLecturaMin} min de lectura`}
                </p>
              </div>
            </Link>

            <div className="flex flex-col gap-7">
              {resto.map((articulo) => (
                <Link
                  key={articulo.slug}
                  href={`/blog/${articulo.slug}`}
                  className="group flex items-center gap-5 rounded-3xl bg-surface p-5 ring-1 ring-line transition-colors hover:ring-gold-deep/30"
                >
                  <div className="flex h-24 w-24 shrink-0 items-center justify-center overflow-hidden rounded-2xl bg-night md:h-28 md:w-28">
                    {articulo.imagenUrl ? (
                      // eslint-disable-next-line @next/next/no-img-element -- enlace externo arbitrario pegado por el admin
                      <img
                        src={articulo.imagenUrl}
                        alt={articulo.titulo}
                        className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
                      />
                    ) : (
                      <span className="font-display text-xs text-gold/40">
                        {articulo.categoria.nombre}
                      </span>
                    )}
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="flex items-center gap-2 text-xs font-medium uppercase tracking-[0.1em] text-gold-deep">
                      {articulo.categoria.nombre}
                      {articulo.tipoContenido === "NOTICIA" && (
                        <span className="rounded-full bg-gold/10 px-2 py-0.5 text-[10px] text-gold-deep">
                          Noticia
                        </span>
                      )}
                    </p>
                    <h3 className="mt-2 font-display text-lg leading-snug md:text-xl">
                      {articulo.titulo}
                    </h3>
                    <p className="mt-2 text-xs text-ink-soft">
                      {formatearFecha(articulo.fechaPublicacion)}
                    </p>
                  </div>
                  <ArrowUpRight
                    weight="bold"
                    className="hidden h-5 w-5 shrink-0 text-ink-soft transition-transform duration-300 group-hover:translate-x-0.5 group-hover:-translate-y-0.5 sm:block"
                  />
                </Link>
              ))}
            </div>
          </div>

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
