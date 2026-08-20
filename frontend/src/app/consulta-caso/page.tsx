"use client";

import { useState, type FormEvent } from "react";
import { motion } from "motion/react";
import { MagnifyingGlass, CheckCircle, Circle, Spinner } from "@phosphor-icons/react";
import { consultarCaso, ApiError, type CasoConsulta, type EtapaCaso } from "@/lib/api";

const EASE = [0.16, 1, 0.3, 1] as const;

const ETAPAS: { valor: EtapaCaso; label: string }[] = [
  { valor: "RADICADO", label: "Radicado" },
  { valor: "EN_ESTUDIO", label: "En estudio" },
  { valor: "EN_TRAMITE", label: "En trámite" },
  { valor: "AUDIENCIA_DILIGENCIA", label: "Audiencia / diligencia" },
  { valor: "RESUELTO", label: "Resuelto" },
];

function formatearFecha(iso: string) {
  return new Date(iso).toLocaleDateString("es-CO", { day: "numeric", month: "long", year: "numeric" });
}

export default function ConsultaCasoPage() {
  const [codigo, setCodigo] = useState("");
  const [cargando, setCargando] = useState(false);
  const [resultado, setResultado] = useState<CasoConsulta | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!codigo.trim()) return;
    setCargando(true);
    setError(null);
    setResultado(null);
    try {
      const caso = await consultarCaso(codigo.trim());
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

  const indiceActual = resultado ? ETAPAS.findIndex((e) => e.valor === resultado.etapa) : -1;

  return (
    <main className="flex-1 pt-32 pb-24 md:pt-36">
      <div className="mx-auto max-w-2xl px-6">
        <h1 className="font-display text-4xl leading-tight tracking-tight md:text-5xl">
          Consulta el estado de tu caso
        </h1>
        <p className="mt-4 text-base leading-relaxed text-ink-soft">
          Ingresa el código único que te enviamos por correo cuando registramos tu caso. No
          necesitas crear ninguna cuenta.
        </p>

        <form onSubmit={onSubmit} className="mt-8 flex flex-col gap-3 sm:flex-row">
          <div className="relative flex-1">
            <MagnifyingGlass
              weight="light"
              className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-soft"
            />
            <input
              value={codigo}
              onChange={(e) => setCodigo(e.target.value.toUpperCase())}
              required
              placeholder="SIE-2026-XXXXXX"
              className="w-full rounded-full border border-line bg-surface py-3.5 pl-10 pr-4 text-sm uppercase tracking-wider text-ink placeholder:text-ink-soft/50 focus:border-gold-deep focus:outline-none"
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
              <p className="font-display text-xl text-ink">{resultado.tipoCaso}</p>
              <p className="font-mono text-xs tracking-wider text-gold-deep">{resultado.codigoUnico}</p>
            </div>
            <p className="mt-1 text-xs text-ink-soft">
              Registrado el {formatearFecha(resultado.fechaCreacion)} · Última actualización{" "}
              {formatearFecha(resultado.fechaActualizacion)}
            </p>

            <ol className="mt-8 space-y-0">
              {ETAPAS.map((etapa, i) => {
                const completada = i < indiceActual;
                const activa = i === indiceActual;
                return (
                  <li key={etapa.valor} className="flex gap-4">
                    <div className="flex flex-col items-center">
                      {completada || activa ? (
                        <CheckCircle
                          weight={activa ? "fill" : "regular"}
                          className={`h-6 w-6 ${activa ? "text-gold" : "text-gold-deep"}`}
                        />
                      ) : (
                        <Circle weight="light" className="h-6 w-6 text-ink-soft/40" />
                      )}
                      {i < ETAPAS.length - 1 && (
                        <span
                          className={`mt-1 h-8 w-px ${i < indiceActual ? "bg-gold-deep" : "bg-line"}`}
                        />
                      )}
                    </div>
                    <p
                      className={`pb-8 text-sm ${
                        activa ? "font-medium text-ink" : completada ? "text-ink" : "text-ink-soft"
                      }`}
                    >
                      {etapa.label}
                      {activa && <span className="ml-2 text-xs text-gold-deep">(etapa actual)</span>}
                    </p>
                  </li>
                );
              })}
            </ol>
          </motion.div>
        )}
      </div>
    </main>
  );
}
