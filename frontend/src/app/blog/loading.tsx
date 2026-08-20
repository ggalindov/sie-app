// Se muestra automáticamente (Next.js la envuelve en un Suspense boundary)
// mientras BlogPage espera la respuesta real del backend (getArticulos/
// getCategorias). Sin esto, una API lenta deja la pantalla en blanco varios
// segundos antes de que aparezca cualquier contenido.
export default function CargandoBlog() {
  return (
    <main className="flex-1 pt-32 pb-24 md:pt-36">
      <div className="mx-auto max-w-6xl px-6">
        <div className="h-11 w-64 animate-pulse rounded-lg bg-ink/8 md:h-14 md:w-80" />
        <div className="mt-4 h-5 w-full max-w-md animate-pulse rounded bg-ink/8" />

        <div className="mt-10 flex flex-wrap gap-2">
          {Array.from({ length: 5 }).map((_, i) => (
            <div key={i} className="h-9 w-24 animate-pulse rounded-full bg-ink/8" />
          ))}
        </div>

        <div className="mt-10 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
          {Array.from({ length: 6 }).map((_, i) => (
            <div key={i} className="overflow-hidden rounded-3xl bg-surface ring-1 ring-line">
              <div className="aspect-[16/10] animate-pulse bg-ink/8" />
              <div className="space-y-3 p-6">
                <div className="h-3 w-20 animate-pulse rounded bg-ink/8" />
                <div className="h-5 w-full animate-pulse rounded bg-ink/8" />
                <div className="h-4 w-4/5 animate-pulse rounded bg-ink/8" />
              </div>
            </div>
          ))}
        </div>
      </div>
    </main>
  );
}
