"use client";

import { useState, useRef, type FormEvent } from "react";
import { motion, AnimatePresence } from "motion/react";
import {
  MagnifyingGlass,
  Spinner,
  Clock,
  Buildings,
  Tag,
  FileText,
  ShieldCheck,
  Scales,
  ArrowRight,
  WhatsappLogo,
  SealCheck,
  ChatCircleText,
  PencilSimple,
  CaretDown,
  CaretUp,
} from "@phosphor-icons/react";
import { consultarCaso, ApiError, type CasoConsulta } from "@/lib/api";
import { siteConfig } from "@/lib/site-config";

const EASE = [0.16, 1, 0.3, 1] as const;

function formatearFecha(iso: string) {
  try {
    return new Date(iso).toLocaleDateString("es-CO", {
      day: "numeric",
      month: "long",
      year: "numeric",
    });
  } catch {
    return iso;
  }
}

export default function ConsultaCasoPage() {
  const [radicado, setRadicado] = useState("");
  const [cargando, setCargando] = useState(false);
  const [resultado, setResultado] = useState<CasoConsulta | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [formularioContraido, setFormularioContraido] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  function abrirFormulario() {
    setFormularioContraido(false);
    setTimeout(() => {
      inputRef.current?.focus();
      inputRef.current?.select();
    }, 150);
  }

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!radicado.trim()) return;
    setCargando(true);
    setError(null);
    try {
      const caso = await consultarCaso(radicado.trim());
      setResultado(caso);
      setFormularioContraido(true);
    } catch (err) {
      setResultado(null);
      setFormularioContraido(false);
      setError(
        err instanceof ApiError
          ? err.message
          : "No pudimos consultar el caso. Verifica el código e intenta de nuevo.",
      );
    } finally {
      setCargando(false);
    }
  }

  return (
    <main className="relative flex-1 overflow-hidden pt-28 pb-24 md:pt-36 md:pb-32">
      {/* Resplandor áureo arquitectónico sutil en cabecera */}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-x-0 top-0 h-[480px] -z-10 select-none opacity-60"
        style={{
          background:
            "radial-gradient(70% 60% at 50% 0%, rgba(217,169,37,0.14) 0%, rgba(217,169,37,0.03) 45%, transparent 75%)",
        }}
      />

      <div className="mx-auto max-w-4xl px-4 sm:px-6 lg:px-8">
        <motion.div layout transition={{ duration: 0.5, ease: EASE }} className="text-center max-w-2xl mx-auto">
          <motion.h1
            layout
            initial={{ opacity: 0, y: 18 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.5, ease: EASE }}
            className={`font-display leading-[1.08] tracking-tight text-ink transition-all duration-500 ${
              formularioContraido ? "text-2xl sm:text-3xl" : "text-4xl sm:text-5xl md:text-6xl"
            }`}
          >
            Consulta el estado de tu caso
          </motion.h1>

          <AnimatePresence initial={false}>
            {!formularioContraido && (
              <motion.p
                key="descripcion-consulta"
                initial={{ opacity: 0, height: 0, marginTop: 0 }}
                animate={{ opacity: 1, height: "auto", marginTop: 16 }}
                exit={{ opacity: 0, height: 0, marginTop: 0 }}
                transition={{ duration: 0.4, ease: EASE }}
                className="text-balance text-base sm:text-lg leading-relaxed text-ink-soft overflow-hidden"
              >
                Ingresa tu número de radicado para conocer en tiempo real las actuaciones,
                audiencias y providencias oficiales registradas por tu equipo jurídico.
              </motion.p>
            )}
          </AnimatePresence>
        </motion.div>

        {/* Tarjeta de Consulta: Se contrae fluidamente cuando se abre el expediente */}
        <motion.div
          layout
          transition={{ duration: 0.5, ease: EASE }}
          className={`rounded-[1.75rem] sm:rounded-[2.25rem] bg-ink/[0.03] ring-1 ring-ink/10 shadow-[0_20px_50px_-20px_rgba(0,0,0,0.06)] transition-all duration-500 ${
            formularioContraido ? "mt-4 p-1 sm:p-1.5" : "mt-8 sm:mt-10 p-1.5 sm:p-2.5"
          }`}
        >
          <motion.div
            layout
            transition={{ duration: 0.5, ease: EASE }}
            className={`rounded-[1.4rem] sm:rounded-[1.9rem] bg-paper ring-1 ring-line transition-all duration-500 ${
              formularioContraido ? "p-3 sm:p-4 px-4 sm:px-6" : "p-5 sm:p-8 md:p-10"
            }`}
          >
            <AnimatePresence mode="wait" initial={false}>
              {formularioContraido ? (
                /* Estado Contraído: Cápsula compacta con acceso rápido a modificar */
                <motion.div
                  key="barra-contraida"
                  initial={{ opacity: 0, y: -6 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -6 }}
                  transition={{ duration: 0.3, ease: EASE }}
                  className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3"
                >
                  <div className="flex items-center gap-3">
                    <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-gold/10 text-gold-deep ring-1 ring-gold/25">
                      <MagnifyingGlass weight="bold" className="h-4 w-4" />
                    </span>
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="text-xs font-semibold uppercase tracking-wider text-ink-soft">
                        Expediente consultado:
                      </span>
                      <span className="font-mono text-xs sm:text-sm font-bold tracking-wider text-ink bg-surface px-2.5 py-1 rounded-lg ring-1 ring-line">
                        {resultado?.radicadoId || radicado}
                      </span>
                    </div>
                  </div>

                  <button
                    type="button"
                    onClick={abrirFormulario}
                    className="inline-flex items-center justify-center gap-2 rounded-xl border border-line bg-surface hover:bg-gold/10 hover:border-gold/40 px-4 py-2 text-xs font-medium text-ink transition-all cursor-pointer group"
                  >
                    <PencilSimple weight="bold" className="h-3.5 w-3.5 text-gold-deep group-hover:scale-110 transition-transform" />
                    <span>Consultar otro radicado</span>
                    <CaretDown weight="bold" className="h-3 w-3 text-ink-soft group-hover:translate-y-0.5 transition-transform" />
                  </button>
                </motion.div>
              ) : (
                /* Estado Expandido: Formulario completo */
                <motion.div
                  key="formulario-expandido"
                  initial={{ opacity: 0, y: -6 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -6 }}
                  transition={{ duration: 0.3, ease: EASE }}
                >
                  <form onSubmit={onSubmit}>
                    <div className="flex items-center justify-between gap-2 mb-2.5">
                      <label
                        htmlFor="radicado-input"
                        className="block text-xs font-semibold uppercase tracking-[0.12em] text-ink-soft"
                      >
                        Número de Radicado o Código de Expediente
                      </label>
                      <div className="flex items-center gap-3">
                        {resultado && (
                          <button
                            type="button"
                            onClick={() => setFormularioContraido(true)}
                            className="inline-flex items-center gap-1 text-xs font-medium text-gold-deep hover:underline cursor-pointer"
                          >
                            <CaretUp weight="bold" className="h-3 w-3" />
                            <span>Contraer consulta</span>
                          </button>
                        )}
                        <span className="hidden sm:inline-flex items-center gap-1 text-xs text-ink-soft/70">
                          <ShieldCheck weight="light" className="h-3.5 w-3.5 text-gold-deep" />
                          Acceso directo sin contraseña
                        </span>
                      </div>
                    </div>

                    <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
                      <div className="relative flex-1">
                        <MagnifyingGlass
                          weight="bold"
                          className="pointer-events-none absolute left-4 top-1/2 h-5 w-5 -translate-y-1/2 text-gold-deep"
                        />
                        <input
                          ref={inputRef}
                          id="radicado-input"
                          value={radicado}
                          onChange={(e) => setRadicado(e.target.value)}
                          required
                          placeholder="Ej: SIE-2024-001 o radicado judicial"
                          className="w-full rounded-xl sm:rounded-2xl border border-line bg-surface py-3.5 pl-11 pr-4 font-mono text-base tracking-wide text-ink placeholder:font-sans placeholder:text-sm placeholder:tracking-normal placeholder:text-ink-soft/45 focus:border-gold-deep focus:bg-paper focus:outline-none focus:ring-2 focus:ring-gold/20 transition-all duration-200"
                        />
                      </div>

                      <button
                        type="submit"
                        disabled={cargando}
                        className="cta-boton group flex shrink-0 items-center justify-center gap-2 rounded-xl sm:rounded-2xl bg-gold px-7 py-3.5 text-sm font-medium text-ink-fixed shadow-sm active:scale-[0.98] disabled:opacity-60 transition-all duration-200 cursor-pointer"
                      >
                        {cargando ? (
                          <>
                            <Spinner className="h-4 w-4 animate-spin text-ink-fixed" weight="bold" />
                            <span>Verificando...</span>
                          </>
                        ) : (
                          <>
                            <span>Consultar expediente</span>
                            <ArrowRight
                              weight="bold"
                              className="h-4 w-4 shrink-0 transition-transform duration-300 group-hover:translate-x-1"
                            />
                          </>
                        )}
                      </button>
                    </div>

                    {/* Ayuda contextual de radicado */}
                    <div className="mt-3.5 flex flex-wrap items-center justify-between gap-2 text-xs text-ink-soft">
                      <span className="flex items-center gap-1.5">
                        <span className="h-1.5 w-1.5 rounded-full bg-gold-deep/60" />
                        Encuentra el código en el correo de apertura o comprobante de tu caso
                      </span>
                      <a
                        href={`${siteConfig.whatsapp}?text=${encodeURIComponent("Hola equipo SIE Jurídicos, necesito consultar el radicado de mi caso y no tengo el código a mano.")}`}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="font-medium text-gold-deep hover:underline transition-colors inline-flex items-center gap-1"
                      >
                        <WhatsappLogo weight="fill" className="h-3.5 w-3.5" />
                        ¿No recuerdas tu radicado? Solicítalo aquí
                      </a>
                    </div>
                  </form>

                  {/* Tres Pilares de Tranquilidad (solo se muestran cuando no hay resultado activo) */}
                  {!resultado && (
                    <div className="mt-8 pt-6 border-t border-line/70 grid grid-cols-1 sm:grid-cols-3 gap-4">
                      <div className="flex items-start gap-3">
                        <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-gold/10 text-gold-deep">
                          <ShieldCheck weight="bold" className="h-4 w-4" />
                        </span>
                        <div>
                          <p className="text-xs font-semibold text-ink uppercase tracking-wide">Confidencialidad Total</p>
                          <p className="mt-0.5 text-xs text-ink-soft leading-relaxed">
                            Tus datos y actuaciones permanecen bajo estricto secreto profesional.
                          </p>
                        </div>
                      </div>

                      <div className="flex items-start gap-3">
                        <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-gold/10 text-gold-deep">
                          <Scales weight="bold" className="h-4 w-4" />
                        </span>
                        <div>
                          <p className="text-xs font-semibold text-ink uppercase tracking-wide">Información Oficial</p>
                          <p className="mt-0.5 text-xs text-ink-soft leading-relaxed">
                            Sincronizada con autos, notificaciones y traslados de los despachos.
                          </p>
                        </div>
                      </div>

                      <div className="flex items-start gap-3">
                        <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-gold/10 text-gold-deep">
                          <SealCheck weight="bold" className="h-4 w-4" />
                        </span>
                        <div>
                          <p className="text-xs font-semibold text-ink uppercase tracking-wide">Soporte Continuo</p>
                          <p className="mt-0.5 text-xs text-ink-soft leading-relaxed">
                            Cuentas con un abogado asignado para resolver inquietudes del proceso.
                          </p>
                        </div>
                      </div>
                    </div>
                  )}
                </motion.div>
              )}
            </AnimatePresence>
          </motion.div>
        </motion.div>

        {/* Notificación de Error */}
        <AnimatePresence>
          {error && (
            <motion.div
              initial={{ opacity: 0, y: 10 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -10 }}
              className="mt-6 rounded-2xl border border-red-200 bg-red-50/90 p-5 text-sm text-red-800"
            >
              <div className="flex items-start gap-3">
                <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-red-100 text-red-700 font-bold text-xs">
                  !
                </span>
                <div className="flex-1">
                  <p className="font-semibold">{error}</p>
                  <p className="mt-1 text-xs text-red-700/80">
                    Asegúrate de haber ingresado el código exacto sin espacios al inicio o al final.
                    Si el problema persiste, puedes escribirnos directamente a WhatsApp con el nombre del titular.
                  </p>
                </div>
              </div>
            </motion.div>
          )}
        </AnimatePresence>

        {/* Visualización del Expediente: Carpeta Procesal Editorial */}
        <AnimatePresence>
          {resultado && (
            <motion.div
              layout
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: 10 }}
              transition={{ duration: 0.5, ease: EASE }}
              className={`rounded-[1.75rem] sm:rounded-[2.25rem] bg-ink/[0.03] p-1.5 sm:p-2.5 ring-1 ring-ink/10 shadow-[0_25px_60px_-25px_rgba(0,0,0,0.08)] transition-all duration-500 ${
                formularioContraido ? "mt-5" : "mt-8 sm:mt-10"
              }`}
            >
              <div className="rounded-[1.4rem] sm:rounded-[1.9rem] bg-paper p-6 sm:p-9 md:p-11 ring-1 ring-line space-y-8">
                {/* Cabecera del Expediente */}
                <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line pb-6">
                  <div>
                    <span className="text-[10px] font-semibold uppercase tracking-[0.16em] text-ink-soft">
                      Expediente Registrado
                    </span>
                    <p className="font-mono text-lg sm:text-xl font-bold tracking-wider text-ink">
                      {resultado.radicadoId}
                    </p>
                  </div>

                  <div className="flex flex-wrap items-center gap-3">
                    <span className="text-xs text-ink-soft">
                      Apertura: <strong className="text-ink font-medium">{formatearFecha(resultado.fechaRegistro)}</strong>
                    </span>

                    {resultado.estadoDisponible ? (
                      <span className="inline-flex items-center gap-1.5 rounded-full bg-gold/15 px-3.5 py-1 text-xs font-semibold text-gold-deep ring-1 ring-gold/30">
                        <span className="h-2 w-2 rounded-full bg-gold-deep animate-pulse" />
                        Seguimiento Activo
                      </span>
                    ) : (
                      <span className="inline-flex items-center gap-1.5 rounded-full bg-ink/5 px-3.5 py-1 text-xs font-medium text-ink-soft ring-1 ring-line">
                        En Preparación
                      </span>
                    )}
                  </div>
                </div>

                {!resultado.estadoDisponible ? (
                  /* Estado: Expediente en etapa inicial */
                  <div className="flex flex-col items-center gap-3 py-10 text-center max-w-md mx-auto">
                    <span className="flex h-14 w-14 items-center justify-center rounded-full bg-gold-pale/70 text-gold-deep ring-1 ring-gold/25">
                      <Clock weight="light" className="h-7 w-7" />
                    </span>
                    <h3 className="font-display text-xl font-semibold text-ink">
                      Expediente radicado en secretaría jurídica
                    </h3>
                    <p className="text-sm leading-relaxed text-ink-soft">
                      Tu proceso se encuentra formalmente abierto en nuestros registros. Nuestro equipo
                      se encuentra preparando las diligencias y documentos pertinentes. En cuanto el despacho
                      judicial notifique la primera providencia, la verás actualizada aquí.
                    </p>
                    <a
                      href={`${siteConfig.whatsapp}?text=${encodeURIComponent(`Hola equipo SIE Jurídicos, quisiera conocer más detalles sobre el inicio de mi expediente ${resultado.radicadoId}.`)}`}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="mt-2 inline-flex items-center gap-2 rounded-xl bg-surface px-5 py-2.5 text-xs font-medium text-ink ring-1 ring-line hover:border-gold-deep transition-all"
                    >
                      <WhatsappLogo weight="fill" className="h-4 w-4 text-[#25D366]" />
                      Consultar con secretaría jurídica
                    </a>
                  </div>
                ) : (
                  /* Estado: Con actuaciones en vivo */
                  <div className="space-y-7">
                    {resultado.estado && (
                      <div className="rounded-2xl border-l-4 border-gold bg-gold-pale/25 p-5 sm:p-7 ring-1 ring-gold/20">
                        <div className="flex flex-wrap items-center justify-between gap-2 mb-2">
                          <span className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-gold-deep">
                            <span className="h-2 w-2 rounded-full bg-gold-deep" />
                            Actuación Procesal Más Reciente
                          </span>
                          {resultado.fechaActualizacionHoja && (
                            <span className="text-xs font-medium text-ink-soft">
                              Actualizado el {resultado.fechaActualizacionHoja}
                            </span>
                          )}
                        </div>
                        <p className="whitespace-pre-line text-sm sm:text-base leading-relaxed text-ink font-medium">
                          {resultado.estado}
                        </p>
                      </div>
                    )}

                    {resultado.ultimaDecision && (
                      <div className="rounded-2xl bg-surface p-5 sm:p-6 ring-1 ring-line">
                        <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-ink-soft mb-2">
                          <Scales weight="light" className="h-4 w-4 text-gold-deep" />
                          Última Decisión / Providencia
                        </div>
                        <p className="whitespace-pre-line text-sm leading-relaxed text-ink">
                          {resultado.ultimaDecision}
                        </p>
                      </div>
                    )}

                    {/* Grilla con Datos del Despacho y Partes */}
                    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                      {resultado.despachoJudicial && (
                        <div className="rounded-2xl bg-surface p-4 sm:p-5 ring-1 ring-line">
                          <span className="flex items-center gap-1.5 text-xs font-medium uppercase tracking-wider text-ink-soft">
                            <Buildings weight="light" className="h-4 w-4 text-gold-deep" />
                            Despacho Judicial
                          </span>
                          <p className="mt-1.5 text-sm font-medium text-ink">{resultado.despachoJudicial}</p>
                        </div>
                      )}

                      {resultado.tipoCaso && (
                        <div className="rounded-2xl bg-surface p-4 sm:p-5 ring-1 ring-line">
                          <span className="flex items-center gap-1.5 text-xs font-medium uppercase tracking-wider text-ink-soft">
                            <Tag weight="light" className="h-4 w-4 text-gold-deep" />
                            Tipo de Proceso
                          </span>
                          <p className="mt-1.5 text-sm font-medium text-ink">{resultado.tipoCaso}</p>
                        </div>
                      )}

                      {resultado.informacionCaso && (
                        <div className="rounded-2xl bg-surface p-4 sm:p-5 ring-1 ring-line sm:col-span-2 lg:col-span-1">
                          <span className="flex items-center gap-1.5 text-xs font-medium uppercase tracking-wider text-ink-soft">
                            <FileText weight="light" className="h-4 w-4 text-gold-deep" />
                            Partes del Proceso
                          </span>
                          <p className="mt-1.5 text-sm font-medium text-ink leading-relaxed">
                            {resultado.informacionCaso}
                          </p>
                        </div>
                      )}
                    </div>

                    {/* Barra de Asistencia con el Abogado */}
                    <div className="rounded-2xl bg-night p-5 sm:p-6 text-night-ink flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
                      <div className="flex items-center gap-3.5">
                        <span className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-gold/15 text-gold ring-1 ring-gold/30">
                          <ChatCircleText weight="fill" className="h-6 w-6" />
                        </span>
                        <div>
                          <p className="text-sm font-semibold text-night-ink">
                            ¿Tienes inquietudes sobre esta actuación en tu caso?
                          </p>
                          <p className="text-xs text-night-ink/65">
                            Tu abogado asignado responderá tus dudas directamente.
                          </p>
                        </div>
                      </div>

                      <a
                        href={`${siteConfig.whatsapp}?text=${encodeURIComponent(`Hola equipo SIE Jurídicos, acabo de consultar mi expediente ${resultado.radicadoId} y quisiera resolver una duda sobre el estado actual.`)}`}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="cta-boton inline-flex shrink-0 items-center justify-center gap-2 rounded-xl bg-gold px-5 py-3 text-xs font-medium text-ink-fixed shadow-sm active:scale-[0.98] transition-all"
                      >
                        <WhatsappLogo weight="fill" className="h-4 w-4" />
                        <span>Hablar con mi abogado</span>
                      </a>
                    </div>
                  </div>
                )}
              </div>
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </main>
  );
}
