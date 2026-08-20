export default function CargandoArticulo() {
  return (
    <main className="flex-1 pt-32 pb-24 md:pt-36">
      <article className="mx-auto max-w-3xl px-6">
        <div className="h-4 w-28 animate-pulse rounded bg-ink/8" />

        <div className="mt-8 h-3 w-24 animate-pulse rounded bg-ink/8" />
        <div className="mt-3 space-y-3">
          <div className="h-9 w-full animate-pulse rounded-lg bg-ink/8 md:h-12" />
          <div className="h-9 w-3/4 animate-pulse rounded-lg bg-ink/8 md:h-12" />
        </div>
        <div className="mt-5 h-4 w-56 animate-pulse rounded bg-ink/8" />

        <div className="mt-10 space-y-4">
          {Array.from({ length: 6 }).map((_, i) => (
            <div key={i} className="h-4 w-full animate-pulse rounded bg-ink/8" />
          ))}
        </div>
      </article>
    </main>
  );
}
