"use client";

import { useState, type FormEvent } from "react";
import { motion } from "motion/react";
import { MagnifyingGlass, Spinner, Clock, Buildings, Tag, FileText } from "@phosphor-icons/react";
import { consultarCaso, ApiError, type CasoConsulta } from "@/lib/api";

const EASE = [0.16, 1, 0.3, 1] as const;

function formatearFecha(iso: string) {
  return new Date(iso).toLocaleDateString("es-CO", { day: "numeric", month: "long", year: "numeric" });
}

export default function ConsultaCasoPage() {
  const [radicado, setRadicado] = useState("");
  const [cargando, setCargando] = useState(false);
  const [resultado, setResultado] = useState<CasoConsulta | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!radicado.trim()) return;
    setCargando(true);
    setError(null);
    setResultado(null);
    try {
      const caso = await consultarCaso(radicado.trim());
      setResultado(caso);
    } catch (err) {
      setError(
        err instanceof ApiError
          ? err.message
          : "No pudimos consultar el caso. Intenta de nuevo.",
      );
    } finally {
      setCargando(false);
    }
  }

  return (
    <main className="flex-1 pt-32 pb-24 md:pt-36">
      <div className="mx-auto max-w-2xl px-6">
        <h1 className="font-display text-4xl leading-tight tracking-tight md:text-5xl">
          Consulta el estado de tu caso
        </h1>
        <p className="mt-4 text-base leading-relaxed text-ink-soft">
          Ingresa el número de radicado que te enviamos por correo cuando registramos tu caso.
          No necesitas crear ninguna cuenta.
        </p>

        <form onSubmit={onSubmit} className="mt-8 flex flex-col gap-3 sm:flex-row">
          <div className="relative flex-1">
            <MagnifyingGlass
              weight="light"
              className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-soft"
            />
            <input
              value={radicado}
              onChange={(e) => setRadicado(e.target.value)}
              required
              placeholder="Número de radicado"
              className="w-full rounded-full border border-line bg-surface py-3.5 pl-10 pr-4 text-sm tracking-wide text-ink placeholder:text-ink-soft/50 focus:border-gold-deep focus:outline-none"
            />
          </div>
          <button
            type="submit"
            disabled={cargando}
            className="cta-boton flex shrink-0 items-center justify-center gap-2 rounded-lg bg-gold px-6 py-3.5 text-sm font-medium text-ink-fixed active:scale-[0.98] disabled:opacity-60"
          >
            {cargando ? <Spinner className="h-4 w-4 animate-spin" weight="bold" /> : "Consultar"}
          </button>
        </form>

        {error && (
          <motion.p
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            className="mt-6 rounded-2xl bg-red-50 px-5 py-4 text-sm text-red-700"
          >
            {error}
          </motion.p>
        )}

        {resultado && (
          <motion.div
            initial={{ opacity: 0, y: 16 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6, ease: EASE }}
            className="mt-10 rounded-3xl bg-surface p-8 ring-1 ring-line"
          >
            <div className="flex flex-wrap items-center justify-between gap-2">
              <p className="font-mono text-xs tracking-wider text-gold-deep">{resultado.radicadoId}</p>
              <p className="text-xs text-ink-soft">Registrado el {formatearFecha(resultado.fechaRegistro)}</p>
            </div>

            {!resultado.estadoDisponible ? (
              <div className="mt-6 flex flex-col items-center gap-3 py-8 text-center">
                <span className="flex h-12 w-12 items-center justify-center rounded-full bg-gold-pale/60 text-gold-deep">
                  <Clock weight="light" className="h-6 w-6" />
                </span>
                <p className="font-display text-lg text-ink">Aún no hay actualizaciones</p>
                <p className="max-w-sm text-sm text-ink-soft">
                  Tu caso está registrado. En cuanto nuestro equipo actualice tu proceso, verás el
                  estado aquí.
                </p>
              </div>
            ) : (
              <div className="mt-6 space-y-6">
                {resultado.estado && (
                  <div>
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <dt className="flex items-center gap-2 text-xs font-medium uppercase tracking-wide text-gold-deep">
                        <span className="h-1.5 w-1.5 shrink-0 rounded-full bg-gold-deep" />
                        Estado actual
                      </dt>
                      {resultado.fechaActualizacionHoja && (
                        <span className="text-xs text-ink-soft">
                          Actualizado el {resultado.fechaActualizacionHoja}
                        </span>
                      )}
                    </div>
                    {/* text-sm leading-relaxed (no font-display grande) a propósito: el
                        texto de esta columna lo escribe libremente el sistema de la firma
                        y puede ser una palabra corta ("En trámite") o un párrafo largo con
                        detalles de audiencia y citaciones -- debe leerse bien en ambos
                        casos, no apretarse en una "pastilla" pensada para texto corto. */}
                    <p className="mt-2 whitespace-pre-line rounded-2xl bg-gold-pale/30 px-5 py-4 text-sm leading-relaxed text-ink">
                      {resultado.estado}
                    </p>
                  </div>
                )}

                {resultado.ultimaDecision && (
                  <div>
                    <dt className="flex items-center gap-2 text-xs font-medium uppercase tracking-wide text-ink-soft">
                      <span className="h-1.5 w-1.5 shrink-0 rounded-full bg-ink-soft/50" />
                      Última decisión
                    </dt>
                    <p className="mt-2 whitespace-pre-line rounded-2xl bg-ink/[0.03] px-5 py-4 text-sm leading-relaxed text-ink">
                      {resultado.ultimaDecision}
                    </p>
                  </div>
                )}

                <dl className="grid gap-5 sm:grid-cols-2">
                  {resultado.despachoJudicial && (
                    <div>
                      <dt className="flex items-center gap-2 text-xs font-medium uppercase tracking-wide text-ink-soft">
                        <Buildings weight="light" className="h-4 w-4" />
                        Despacho judicial
                      </dt>
                      <dd className="mt-1.5 text-sm text-ink">{resultado.despachoJudicial}</dd>
                    </div>
                  )}
                  {resultado.tipoCaso && (
                    <div>
                      <dt className="flex items-center gap-2 text-xs font-medium uppercase tracking-wide text-ink-soft">
                        <Tag weight="light" className="h-4 w-4" />
                        Tipo de caso
                      </dt>
                      <dd className="mt-1.5 text-sm text-ink">{resultado.tipoCaso}</dd>
                    </div>
                  )}
                  {resultado.informacionCaso && (
                    <div className="sm:col-span-2">
                      <dt className="flex items-center gap-2 text-xs font-medium uppercase tracking-wide text-ink-soft">
                        <FileText weight="light" className="h-4 w-4" />
                        Partes del proceso
                      </dt>
                      <dd className="mt-1.5 text-sm leading-relaxed text-ink">{resultado.informacionCaso}</dd>
                    </div>
                  )}
                </dl>
              </div>
            )}
          </motion.div>
        )}
      </div>
    </main>
  );
}
