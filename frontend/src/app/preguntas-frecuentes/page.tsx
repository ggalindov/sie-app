import type { Metadata } from "next";
import { Accordion } from "@base-ui/react/accordion";
import { CaretDown } from "@phosphor-icons/react/dist/ssr";
import { getFaq } from "@/lib/api";

export const metadata: Metadata = {
  title: "Preguntas frecuentes",
  description: "Respuestas a las preguntas que con más frecuencia recibimos de nuestros clientes.",
};

export default async function PreguntasFrecuentesPage() {
  const preguntas = await getFaq().catch(() => []);

  return (
    <main className="flex-1 pt-32 pb-24 md:pt-36">
      <div className="mx-auto max-w-3xl px-6">
        <h1 className="font-display text-4xl leading-tight tracking-tight md:text-5xl">
          Preguntas frecuentes
        </h1>
        <p className="mt-4 max-w-lg text-base leading-relaxed text-ink-soft">
          Estas son las preguntas que con más frecuencia nos hacen a través de nuestro
          asistente virtual. Si no encuentras lo que buscas, escríbenos directamente.
        </p>

        {preguntas.length === 0 ? (
          <p className="mt-16 text-center text-sm text-ink-soft">
            Aún no hay preguntas frecuentes publicadas.
          </p>
        ) : (
          <Accordion.Root className="mt-10 divide-y divide-line rounded-3xl bg-surface px-6 ring-1 ring-line">
            {preguntas.map((p) => (
              <Accordion.Item key={p.id} className="py-2">
                <Accordion.Header>
                  <Accordion.Trigger className="group flex w-full items-center justify-between gap-4 py-4 text-left">
                    <span className="font-display text-lg leading-snug text-ink">{p.pregunta}</span>
                    <CaretDown
                      weight="bold"
                      className="h-4 w-4 shrink-0 text-ink-soft transition-transform duration-300 group-data-[panel-open]:rotate-180"
                    />
                  </Accordion.Trigger>
                </Accordion.Header>
                <Accordion.Panel className="overflow-hidden text-sm leading-relaxed text-ink-soft data-[ending-style]:h-0 data-[starting-style]:h-0">
                  <p className="pb-5">{p.respuesta}</p>
                </Accordion.Panel>
              </Accordion.Item>
            ))}
          </Accordion.Root>
        )}
      </div>
    </main>
  );
}
