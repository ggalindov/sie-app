"use client";

import { useState, type FormEvent } from "react";
import { motion, AnimatePresence } from "motion/react";
import {
  PaperPlaneTilt,
  MagnifyingGlass,
  Spinner,
  CheckCircle,
  Copy,
  Check,
  Clock,
  ChatCircleText,
  WhatsappLogo,
  Scroll,
  Warning,
  Receipt,
  Lightbulb,
} from "@phosphor-icons/react";
import {
  crearPqrs,
  consultarPqrs,
  ApiError,
  type TipoPqrs,
  type PqrsCreada,
  type PqrsConsulta,
} from "@/lib/api";
import { siteConfig } from "@/lib/site-config";
import { CampoTrampa } from "@/components/campo-trampa";
import { CasillaConsentimiento } from "@/components/casilla-consentimiento";

const EASE = [0.16, 1, 0.3, 1] as const;

const TIPOS: { valor: TipoPqrs; etiqueta: string; descripcion: string; icono: typeof Scroll }[] = [
  { valor: "PETICION", etiqueta: "Petición", descripcion: "Una solicitud o consulta formal", icono: Scroll },
  { valor: "QUEJA", etiqueta: "Queja", descripcion: "Una inconformidad con la atención recibida", icono: Warning },
  { valor: "RECLAMO", etiqueta: "Reclamo", descripcion: "Una exigencia sobre un servicio prestado", icono: Receipt },
  { valor: "SUGERENCIA", etiqueta: "Sugerencia", descripcion: "Una idea para mejorar nuestro servicio", icono: Lightbulb },
];

const ESTADO_VISIBLE: Record<PqrsConsulta["estado"], { etiqueta: string; tono: string }> = {
  RECIBIDO: { etiqueta: "Recibido", tono: "bg-ink/8 text-ink-soft" },
  EN_TRAMITE: { etiqueta: "En trámite", tono: "bg-gold/15 text-gold-deep" },
  RESUELTO: { etiqueta: "Resuelto", tono: "bg-emerald-100 text-emerald-800" },
  CERRADO: { etiqueta: "Cerrado", tono: "bg-ink/8 text-ink-soft" },
};

function formatearFecha(iso: string) {
  try {
    return new Date(iso).toLocaleDateString("es-CO", { day: "numeric", month: "long", year: "numeric" });
  } catch {
    return iso;
  }
}

export default function PqrsPage() {
  const [modo, setModo] = useState<"radicar" | "consultar">("radicar");

  return (
    <main className="relative flex-1 overflow-hidden pt-28 pb-24 md:pt-36 md:pb-32">
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-x-0 top-0 h-[480px] -z-10 select-none opacity-60"
        style={{
          background:
            "radial-gradient(70% 60% at 50% 0%, rgba(217,169,37,0.14) 0%, rgba(217,169,37,0.03) 45%, transparent 75%)",
        }}
      />

      <div className="mx-auto max-w-3xl px-4 sm:px-6 lg:px-8">
        <motion.div
          initial={{ opacity: 0, y: 18 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, ease: EASE }}
          className="text-center"
        >
          <p className="text-xs font-semibold uppercase tracking-widest text-gold-deep">
            Atención al cliente
          </p>
          <h1 className="mt-2 font-display text-4xl leading-[1.08] tracking-tight text-ink sm:text-5xl md:text-6xl">
            Peticiones, Quejas, Reclamos y Sugerencias
          </h1>
          <p className="mx-auto mt-4 max-w-xl text-balance text-base leading-relaxed text-ink-soft sm:text-lg">
            Radica tu PQRS y recibe un número de radicado para hacerle seguimiento, sin
            necesidad de crear ninguna cuenta.
          </p>
        </motion.div>

        <motion.div
          initial={{ opacity: 0, y: 16 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, delay: 0.1, ease: EASE }}
          className="mt-9 flex items-center justify-center gap-1 rounded-full bg-ink/5 p-1"
        >
          {(["radicar", "consultar"] as const).map((valor) => (
            <button
              key={valor}
              type="button"
              onClick={() => setModo(valor)}
              className={`relative rounded-full px-5 py-2.5 text-sm font-medium transition-colors ${
                modo === valor ? "text-ink-fixed" : "text-ink-soft hover:text-ink"
              }`}
            >
              {modo === valor && (
                <motion.span
                  layoutId="pqrs-tab-activo"
                  className="absolute inset-0 rounded-full bg-gold"
                  transition={{ duration: 0.35, ease: EASE }}
                />
              )}
              <span className="relative z-10">
                {valor === "radicar" ? "Radicar una PQRS" : "Consultar el estado"}
              </span>
            </button>
          ))}
        </motion.div>

        <div className="mt-8 sm:mt-10 rounded-[1.75rem] sm:rounded-[2.25rem] bg-ink/[0.03] p-1.5 sm:p-2.5 ring-1 ring-ink/10 shadow-[0_20px_50px_-20px_rgba(0,0,0,0.06)]">
          <div className="rounded-[1.4rem] sm:rounded-[1.9rem] bg-paper p-5 sm:p-8 md:p-10 ring-1 ring-line">
            <AnimatePresence mode="wait" initial={false}>
              {modo === "radicar" ? (
                <motion.div
                  key="radicar"
                  initial={{ opacity: 0, y: 8 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -8 }}
                  transition={{ duration: 0.3, ease: EASE }}
                >
                  <FormularioRadicar />
                </motion.div>
              ) : (
                <motion.div
                  key="consultar"
                  initial={{ opacity: 0, y: 8 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -8 }}
                  transition={{ duration: 0.3, ease: EASE }}
                >
                  <FormularioConsultar />
                </motion.div>
              )}
            </AnimatePresence>
          </div>
        </div>
      </div>
    </main>
  );
}

function FormularioRadicar() {
  const [tipo, setTipo] = useState<TipoPqrs>("PETICION");
  const [aceptaDatos, setAceptaDatos] = useState(false);
  const [errorDatos, setErrorDatos] = useState(false);
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [creada, setCreada] = useState<PqrsCreada | null>(null);
  const [copiado, setCopiado] = useState(false);

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!aceptaDatos) {
      setErrorDatos(true);
      return;
    }
    const form = e.currentTarget;
    const data = new FormData(form);
    setEnviando(true);
    setError(null);
    try {
      const resultado = await crearPqrs({
        tipo,
        nombre: String(data.get("nombre") ?? ""),
        correo: String(data.get("correo") ?? ""),
        telefono: String(data.get("telefono") ?? "") || undefined,
        asunto: String(data.get("asunto") ?? ""),
        descripcion: String(data.get("descripcion") ?? ""),
        sitioWeb: String(data.get("sitioWeb") ?? ""),
      });
      setCreada(resultado);
      form.reset();
    } catch (err) {
      setError(
        err instanceof ApiError
          ? err.message
          : "No pudimos radicar tu solicitud. Intenta de nuevo en unos minutos.",
      );
    } finally {
      setEnviando(false);
    }
  }

  function copiarRadicado() {
    if (!creada) return;
    navigator.clipboard?.writeText(creada.radicado).then(() => {
      setCopiado(true);
      setTimeout(() => setCopiado(false), 2000);
    });
  }

  if (creada) {
    return (
      <div className="flex flex-col items-center py-4 text-center">
        <span className="flex h-14 w-14 items-center justify-center rounded-full bg-emerald-100 text-emerald-700">
          <CheckCircle weight="fill" className="h-8 w-8" />
        </span>
        <h2 className="mt-5 font-display text-2xl text-ink">Quedó radicada</h2>
        <p className="mt-2 max-w-sm text-sm leading-relaxed text-ink-soft">
          Te enviamos este mismo radicado a tu correo. Guárdalo para consultar el estado
          cuando quieras.
        </p>

        <button
          type="button"
          onClick={copiarRadicado}
          className="mt-6 flex items-center gap-3 rounded-2xl border border-dashed border-gold-deep/50 bg-gold/5 px-6 py-4 transition-colors hover:bg-gold/10"
        >
          <span className="font-mono text-xl font-bold tracking-[0.2em] text-ink">{creada.radicado}</span>
          {copiado ? (
            <Check weight="bold" className="h-4 w-4 text-emerald-600" />
          ) : (
            <Copy weight="bold" className="h-4 w-4 text-gold-deep" />
          )}
        </button>

        <p className="mt-5 flex items-center gap-1.5 text-xs text-ink-soft">
          <Clock weight="light" className="h-4 w-4 text-gold-deep" />
          Plazo de referencia de respuesta: {formatearFecha(creada.fechaLimiteRespuesta)}
        </p>

        <button
          type="button"
          onClick={() => setCreada(null)}
          className="mt-7 text-sm font-medium text-gold-deep underline underline-offset-4"
        >
          Radicar otra PQRS
        </button>
      </div>
    );
  }

  return (
    <form onSubmit={onSubmit} className="space-y-5">
      <CampoTrampa />

      <div>
        <p className="text-xs font-semibold uppercase tracking-[0.1em] text-ink-soft">Tipo de solicitud</p>
        <div className="mt-2.5 grid grid-cols-2 gap-2.5 sm:grid-cols-4">
          {TIPOS.map((t) => {
            const Icono = t.icono;
            const activo = tipo === t.valor;
            return (
              <button
                key={t.valor}
                type="button"
                onClick={() => setTipo(t.valor)}
                title={t.descripcion}
                className={`flex flex-col items-center gap-1.5 rounded-xl border px-3 py-3 text-center transition-all ${
                  activo
                    ? "border-gold-deep bg-gold/10 text-ink"
                    : "border-line bg-surface text-ink-soft hover:border-gold-deep/40"
                }`}
              >
                <Icono weight={activo ? "fill" : "light"} className={`h-5 w-5 ${activo ? "text-gold-deep" : ""}`} />
                <span className="text-xs font-medium">{t.etiqueta}</span>
              </button>
            );
          })}
        </div>
      </div>

      <div className="grid grid-cols-2 gap-4">
        <div className="col-span-2 space-y-1.5 sm:col-span-1">
          <label htmlFor="pq-nombre" className="text-sm font-medium text-ink">Nombre</label>
          <input
            id="pq-nombre"
            name="nombre"
            required
            maxLength={150}
            className="w-full rounded-xl border border-line bg-surface px-4 py-2.5 text-sm text-ink transition-all duration-200 focus:border-gold-deep focus:outline-none focus:ring-4 focus:ring-gold/10"
          />
        </div>
        <div className="col-span-2 space-y-1.5 sm:col-span-1">
          <label htmlFor="pq-correo" className="text-sm font-medium text-ink">Correo</label>
          <input
            id="pq-correo"
            name="correo"
            type="email"
            required
            className="w-full rounded-xl border border-line bg-surface px-4 py-2.5 text-sm text-ink transition-all duration-200 focus:border-gold-deep focus:outline-none focus:ring-4 focus:ring-gold/10"
          />
        </div>
        <div className="col-span-2 space-y-1.5 sm:col-span-1">
          <label htmlFor="pq-telefono" className="text-sm font-medium text-ink">
            Teléfono <span className="text-xs font-normal text-ink-soft">(opcional)</span>
          </label>
          <input
            id="pq-telefono"
            name="telefono"
            maxLength={30}
            className="w-full rounded-xl border border-line bg-surface px-4 py-2.5 text-sm text-ink transition-all duration-200 focus:border-gold-deep focus:outline-none focus:ring-4 focus:ring-gold/10"
          />
        </div>
        <div className="col-span-2 space-y-1.5 sm:col-span-1">
          <label htmlFor="pq-asunto" className="text-sm font-medium text-ink">Asunto</label>
          <input
            id="pq-asunto"
            name="asunto"
            required
            maxLength={200}
            className="w-full rounded-xl border border-line bg-surface px-4 py-2.5 text-sm text-ink transition-all duration-200 focus:border-gold-deep focus:outline-none focus:ring-4 focus:ring-gold/10"
          />
        </div>
      </div>

      <div className="space-y-1.5">
        <label htmlFor="pq-descripcion" className="text-sm font-medium text-ink">Describe tu solicitud</label>
        <textarea
          id="pq-descripcion"
          name="descripcion"
          required
          rows={5}
          maxLength={3000}
          className="w-full resize-none rounded-xl border border-line bg-surface px-4 py-2.5 text-sm text-ink transition-all duration-200 focus:border-gold-deep focus:outline-none focus:ring-4 focus:ring-gold/10"
          placeholder="Cuéntanos con el mayor detalle posible lo sucedido"
        />
      </div>

      <CasillaConsentimiento
        checked={aceptaDatos}
        onChange={(v) => {
          setAceptaDatos(v);
          setErrorDatos(false);
        }}
      >
        Autorizo el tratamiento de mis datos personales conforme a la política de privacidad
        de SIE Jurídicos (Ley 1581 de 2012).
      </CasillaConsentimiento>
      {errorDatos && <p className="text-xs text-red-500">Debes aceptar el tratamiento de datos para continuar.</p>}

      {error && (
        <p className="rounded-xl bg-red-50 px-4 py-3 text-xs text-red-700">{error}</p>
      )}

      <button
        type="submit"
        disabled={enviando}
        className="cta-boton flex w-full items-center justify-center gap-2 rounded-xl bg-gold py-3 text-sm font-medium text-ink-fixed transition-opacity disabled:opacity-60"
      >
        {enviando ? (
          <Spinner className="h-4 w-4 animate-spin" weight="bold" />
        ) : (
          <PaperPlaneTilt weight="fill" className="h-4 w-4" />
        )}
        {enviando ? "Enviando" : "Radicar PQRS"}
      </button>
    </form>
  );
}

function FormularioConsultar() {
  const [radicado, setRadicado] = useState("");
  const [cargando, setCargando] = useState(false);
  const [resultado, setResultado] = useState<PqrsConsulta | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!radicado.trim()) return;
    setCargando(true);
    setError(null);
    try {
      const data = await consultarPqrs(radicado.trim());
      setResultado(data);
    } catch (err) {
      setResultado(null);
      setError(
        err instanceof ApiError
          ? err.message
          : "No pudimos consultar tu PQRS. Verifica el radicado e intenta de nuevo.",
      );
    } finally {
      setCargando(false);
    }
  }

  return (
    <div>
      <form onSubmit={onSubmit} className="flex flex-col gap-3 sm:flex-row sm:items-center">
        <div className="relative flex-1">
          <MagnifyingGlass
            weight="bold"
            className="pointer-events-none absolute left-4 top-1/2 h-5 w-5 -translate-y-1/2 text-gold-deep"
          />
          <input
            value={radicado}
            onChange={(e) => setRadicado(e.target.value)}
            required
            placeholder="Ej: PQRS-7K2P9M"
            className="w-full rounded-xl border border-line bg-surface py-3.5 pl-11 pr-4 font-mono text-base tracking-wide text-ink placeholder:font-sans placeholder:text-sm placeholder:tracking-normal placeholder:text-ink-soft/45 focus:border-gold-deep focus:outline-none"
          />
        </div>
        <button
          type="submit"
          disabled={cargando}
          className="cta-boton flex shrink-0 items-center justify-center gap-2 rounded-xl bg-gold px-6 py-3.5 text-sm font-medium text-ink-fixed disabled:opacity-60"
        >
          {cargando ? <Spinner className="h-4 w-4 animate-spin" weight="bold" /> : "Consultar"}
        </button>
      </form>

      {error && <p className="mt-4 rounded-xl bg-red-50 px-4 py-3 text-xs text-red-700">{error}</p>}

      <AnimatePresence>
        {resultado && (
          <motion.div
            initial={{ opacity: 0, y: 14 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -10 }}
            transition={{ duration: 0.4, ease: EASE }}
            className="mt-7 space-y-6 rounded-2xl bg-surface p-5 sm:p-7 ring-1 ring-line"
          >
            <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line pb-5">
              <div>
                <span className="text-[10px] font-semibold uppercase tracking-[0.16em] text-ink-soft">
                  {TIPOS.find((t) => t.valor === resultado.tipo)?.etiqueta ?? resultado.tipo}
                </span>
                <p className="font-mono text-lg font-bold tracking-wider text-ink">{resultado.radicado}</p>
              </div>
              <span className={`inline-flex items-center rounded-full px-3.5 py-1 text-xs font-semibold ${ESTADO_VISIBLE[resultado.estado].tono}`}>
                {ESTADO_VISIBLE[resultado.estado].etiqueta}
              </span>
            </div>

            <div>
              <p className="text-xs font-medium uppercase tracking-wide text-ink-soft">Asunto</p>
              <p className="mt-1 text-sm font-medium text-ink">{resultado.asunto}</p>
            </div>

            {resultado.respuesta ? (
              <div className="rounded-xl border-l-4 border-gold bg-gold-pale/25 p-4 sm:p-5">
                <p className="flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-gold-deep">
                  <ChatCircleText weight="fill" className="h-4 w-4" />
                  Respuesta de la firma
                </p>
                <p className="mt-2 whitespace-pre-line text-sm leading-relaxed text-ink">{resultado.respuesta}</p>
                {resultado.fechaRespuesta && (
                  <p className="mt-2 text-xs text-ink-soft">Respondida el {formatearFecha(resultado.fechaRespuesta)}</p>
                )}
              </div>
            ) : (
              <div className="flex items-center gap-3 rounded-xl bg-ink/[0.03] p-4">
                <Clock weight="light" className="h-5 w-5 shrink-0 text-gold-deep" />
                <p className="text-sm text-ink-soft">
                  Aún estamos trabajando en tu solicitud. Plazo de referencia de respuesta:{" "}
                  <strong className="text-ink">{formatearFecha(resultado.fechaLimiteRespuesta)}</strong>.
                </p>
              </div>
            )}

            <div className="flex items-center justify-between gap-4 rounded-xl bg-night p-4 text-night-ink">
              <p className="text-xs">Radicada el {formatearFecha(resultado.fechaCreacion)}</p>
              <a
                href={`${siteConfig.whatsapp}?text=${encodeURIComponent(`Hola equipo SIE Jurídicos, quisiera más información sobre mi PQRS ${resultado.radicado}.`)}`}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-1.5 text-xs font-medium text-gold hover:underline"
              >
                <WhatsappLogo weight="fill" className="h-3.5 w-3.5" />
                Hablar con nosotros
              </a>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
