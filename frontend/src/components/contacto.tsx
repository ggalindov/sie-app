"use client";

import { useState } from "react";
import { motion } from "motion/react";
import Link from "next/link";
import { toast } from "sonner";
import {
  ArrowSquareOut,
  Check,
  Copy,
  EnvelopeSimple,
  FacebookLogo,
  FileMagnifyingGlass,
  InstagramLogo,
  LinkedinLogo,
  MapPin,
  Phone,
  ShieldCheck,
  WhatsappLogo,
  YoutubeLogo,
} from "@phosphor-icons/react";
import { siteConfig } from "@/lib/site-config";

const EASE = [0.16, 1, 0.3, 1] as const;

export function Contacto() {
  const [copiado, setCopiado] = useState<string | null>(null);

  function copiarAlPortapapeles(texto: string, etiqueta: string) {
    if (navigator?.clipboard) {
      navigator.clipboard.writeText(texto);
      setCopiado(etiqueta);
      toast.success(`${etiqueta} copiado al portapapeles`);
      setTimeout(() => setCopiado(null), 2500);
    }
  }

  return (
    <section id="contacto" className="snap-slide section-seam frame-fixed bg-surface/90 overflow-hidden">
      <div className="py-12 sm:py-16 md:flex md:h-full md:flex-col md:justify-center md:overflow-y-auto md:py-12">
        <div className="mx-auto max-w-7xl px-4 sm:px-6 w-full">
          <div className="grid gap-10 md:grid-cols-12 md:gap-12 lg:gap-16 items-start">
            {/* Columna Izquierda: Información de Cabecera y Protección de Datos */}
            <motion.div
              initial={{ opacity: 0, y: 24 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true, amount: 0.3 }}
              transition={{ duration: 0.7, ease: EASE }}
              className="md:col-span-5 flex flex-col justify-between"
            >
              <div>
                <h2 className="font-display text-3xl sm:text-4xl leading-tight tracking-tight md:text-5xl text-ink">
                  Contacto
                </h2>
                <p className="mt-3.5 max-w-md text-sm sm:text-base leading-relaxed text-ink-soft">
                  Comunícate directamente con nuestro equipo de abogados. Elige el medio que prefieras para una respuesta ágil y confidencial.
                </p>

                {/* Redes Sociales Oficiales */}
                <div className="mt-6 flex flex-wrap items-center gap-2.5">
                  <a
                    href={siteConfig.redes.facebook}
                    target="_blank"
                    rel="noopener noreferrer"
                    aria-label="Facebook"
                    title="Facebook"
                    className="flex h-10 w-10 items-center justify-center rounded-full bg-ink/5 text-ink-soft transition hover:bg-gold hover:text-ink-fixed ring-1 ring-line/60"
                  >
                    <FacebookLogo weight="bold" className="h-5 w-5" />
                  </a>
                  <a
                    href={siteConfig.redes.linkedin}
                    target="_blank"
                    rel="noopener noreferrer"
                    aria-label="LinkedIn"
                    title="LinkedIn"
                    className="flex h-10 w-10 items-center justify-center rounded-full bg-ink/5 text-ink-soft transition hover:bg-gold hover:text-ink-fixed ring-1 ring-line/60"
                  >
                    <LinkedinLogo weight="bold" className="h-5 w-5" />
                  </a>
                  <a
                    href={siteConfig.redes.instagram}
                    target="_blank"
                    rel="noopener noreferrer"
                    aria-label="Instagram"
                    title="Instagram"
                    className="flex h-10 w-10 items-center justify-center rounded-full bg-ink/5 text-ink-soft transition hover:bg-gold hover:text-ink-fixed ring-1 ring-line/60"
                  >
                    <InstagramLogo weight="bold" className="h-5 w-5" />
                  </a>
                  <a
                    href={siteConfig.redes.youtube}
                    target="_blank"
                    rel="noopener noreferrer"
                    aria-label="YouTube"
                    title="YouTube"
                    className="flex h-10 w-10 items-center justify-center rounded-full bg-ink/5 text-ink-soft transition hover:bg-gold hover:text-ink-fixed ring-1 ring-line/60"
                  >
                    <YoutubeLogo weight="bold" className="h-5 w-5" />
                  </a>
                  <a
                    href={siteConfig.whatsapp}
                    target="_blank"
                    rel="noopener noreferrer"
                    aria-label="WhatsApp"
                    title="WhatsApp"
                    className="flex h-10 w-10 items-center justify-center rounded-full bg-ink/5 text-ink-soft transition hover:bg-gold hover:text-ink-fixed ring-1 ring-line/60"
                  >
                    <WhatsappLogo weight="bold" className="h-5 w-5" />
                  </a>
                </div>
              </div>

              {/* Protección de Datos (Habeas Data) */}
              <div className="mt-8 rounded-2xl bg-ink/5 p-4 sm:p-5 ring-1 ring-ink/10">
                <div className="flex items-center gap-2.5">
                  <ShieldCheck weight="fill" className="h-5 w-5 shrink-0 text-gold-deep" />
                  <p className="text-xs font-semibold uppercase tracking-[0.08em] text-ink">
                    Protección de datos y secreto profesional
                  </p>
                </div>
                <ul className="mt-2.5 space-y-1.5 text-xs leading-relaxed text-ink-soft">
                  <li>
                    Tratamiento regulado bajo Ley 1581 de 2012 (Habeas Data): tus datos solo se emplean para responder tu caso.
                  </li>
                  <li>
                    Amparo bajo reserva legal y secreto profesional abogado-cliente.
                  </li>
                  <li>Toda comunicación en este portal viaja bajo cifrado seguro TLS/HTTPS.</li>
                </ul>
              </div>
            </motion.div>

            {/* Columna Derecha: Tarjetas Interactivas y Funcionales */}
            <div className="flex flex-col gap-3.5 sm:gap-4 md:col-span-7">
              {/* 1. Tarjeta Destacada: WhatsApp Inmediato */}
              <motion.div
                initial={{ opacity: 0, y: 16 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true, amount: 0.3 }}
                transition={{ duration: 0.5, ease: EASE }}
                className="card-edged relative overflow-hidden rounded-2xl bg-gradient-to-r from-emerald-950/20 via-surface to-surface p-4 sm:p-5 ring-1 ring-emerald-500/20 transition-all hover:ring-emerald-500/40"
              >
                <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
                  <div className="flex items-center gap-3.5">
                    <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-[#25d366]/20 text-[#25d366] ring-1 ring-[#25d366]/30">
                      <WhatsappLogo weight="fill" className="h-6 w-6" />
                    </span>
                    <div>
                      <div className="flex items-center gap-2">
                        <span className="text-xs font-semibold uppercase tracking-wider text-emerald-700 dark:text-emerald-400">
                          WhatsApp Oficial
                        </span>
                        <span className="inline-flex items-center rounded-full bg-emerald-500/10 px-2 py-0.5 text-[10px] font-medium text-emerald-600 dark:text-emerald-400">
                          Respuesta en minutos
                        </span>
                      </div>
                      <p className="mt-0.5 text-base sm:text-lg font-bold text-ink">
                        {siteConfig.telefono}
                      </p>
                      <p className="text-xs text-ink-soft">Chatea directamente con nuestro equipo jurídico</p>
                    </div>
                  </div>

                  <div className="flex items-center gap-2 self-end sm:self-center shrink-0">
                    <button
                      type="button"
                      onClick={() => copiarAlPortapapeles(siteConfig.telefono, "Número de WhatsApp")}
                      className="inline-flex items-center gap-1.5 rounded-full border border-line bg-surface px-3 py-1.5 text-xs font-medium text-ink-soft transition hover:border-gold-deep hover:text-ink"
                    >
                      {copiado === "Número de WhatsApp" ? (
                        <>
                          <Check className="h-3.5 w-3.5 text-emerald-500" weight="bold" /> Copiado
                        </>
                      ) : (
                        <>
                          <Copy className="h-3.5 w-3.5" /> Copiar
                        </>
                      )}
                    </button>
                    <a
                      href={siteConfig.whatsapp}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="inline-flex items-center gap-1.5 rounded-full bg-[#25d366] px-4 py-1.5 text-xs font-semibold text-white shadow-sm transition hover:bg-[#1faa4f]"
                    >
                      <span>Abrir chat</span>
                      <ArrowSquareOut className="h-3.5 w-3.5" weight="bold" />
                    </a>
                  </div>
                </div>
              </motion.div>

              {/* 2. Tarjeta Interactiva: Teléfono */}
              <motion.div
                initial={{ opacity: 0, y: 16 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true, amount: 0.3 }}
                transition={{ duration: 0.5, delay: 0.06, ease: EASE }}
                className="card-edged rounded-2xl bg-surface p-4 sm:p-5 ring-1 ring-line/80 transition-all hover:ring-gold-deep/30"
              >
                <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
                  <div className="flex items-center gap-3.5">
                    <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-gold-pale/60 text-gold-deep">
                      <Phone weight="bold" className="h-5 w-5" />
                    </span>
                    <div>
                      <p className="text-xs font-semibold uppercase tracking-wider text-ink-soft">
                        Línea Telefónica Directa
                      </p>
                      <p className="mt-0.5 text-base sm:text-lg font-bold text-ink">
                        {siteConfig.telefono}
                      </p>
                      <p className="text-xs text-ink-soft">Atención para citas, consultas y empresas</p>
                    </div>
                  </div>

                  <div className="flex items-center gap-2 self-end sm:self-center shrink-0">
                    <button
                      type="button"
                      onClick={() => copiarAlPortapapeles(siteConfig.telefono, "Teléfono")}
                      className="inline-flex items-center gap-1.5 rounded-full border border-line bg-surface px-3 py-1.5 text-xs font-medium text-ink-soft transition hover:border-gold-deep hover:text-ink"
                    >
                      {copiado === "Teléfono" ? (
                        <>
                          <Check className="h-3.5 w-3.5 text-emerald-500" weight="bold" /> Copiado
                        </>
                      ) : (
                        <>
                          <Copy className="h-3.5 w-3.5" /> Copiar
                        </>
                      )}
                    </button>
                    <a
                      href={siteConfig.telefonoHref}
                      className="inline-flex items-center gap-1.5 rounded-full bg-gold-deep px-4 py-1.5 text-xs font-semibold text-white shadow-sm transition hover:bg-gold"
                    >
                      <span>Llamar</span>
                      <Phone className="h-3.5 w-3.5" weight="bold" />
                    </a>
                  </div>
                </div>
              </motion.div>

              {/* 3. Tarjeta Interactiva: Correo Institucional */}
              <motion.div
                initial={{ opacity: 0, y: 16 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true, amount: 0.3 }}
                transition={{ duration: 0.5, delay: 0.12, ease: EASE }}
                className="card-edged rounded-2xl bg-surface p-4 sm:p-5 ring-1 ring-line/80 transition-all hover:ring-gold-deep/30"
              >
                <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
                  <div className="flex items-center gap-3.5 min-w-0">
                    <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-gold-pale/60 text-gold-deep">
                      <EnvelopeSimple weight="bold" className="h-5 w-5" />
                    </span>
                    <div className="min-w-0">
                      <p className="text-xs font-semibold uppercase tracking-wider text-ink-soft">
                        Correo Institucional
                      </p>
                      <p className="mt-0.5 text-sm sm:text-base font-bold text-ink truncate">
                        {siteConfig.correo}
                      </p>
                      <p className="text-xs text-ink-soft truncate">Envío de documentos, expedientes y propuestas</p>
                    </div>
                  </div>

                  <div className="flex items-center gap-2 self-end sm:self-center shrink-0">
                    <button
                      type="button"
                      onClick={() => copiarAlPortapapeles(siteConfig.correo, "Correo")}
                      className="inline-flex items-center gap-1.5 rounded-full border border-line bg-surface px-3 py-1.5 text-xs font-medium text-ink-soft transition hover:border-gold-deep hover:text-ink"
                    >
                      {copiado === "Correo" ? (
                        <>
                          <Check className="h-3.5 w-3.5 text-emerald-500" weight="bold" /> Copiado
                        </>
                      ) : (
                        <>
                          <Copy className="h-3.5 w-3.5" /> Copiar
                        </>
                      )}
                    </button>
                    <a
                      href={`mailto:${siteConfig.correo}`}
                      className="inline-flex items-center gap-1.5 rounded-full border border-line bg-surface px-3.5 py-1.5 text-xs font-semibold text-ink transition hover:border-gold-deep hover:text-gold-deep"
                    >
                      <span>Escribir</span>
                      <ArrowSquareOut className="h-3.5 w-3.5" weight="bold" />
                    </a>
                  </div>
                </div>
              </motion.div>

              {/* 4. Tarjeta Interactiva: Ubicación y Cobertura */}
              <motion.div
                initial={{ opacity: 0, y: 16 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true, amount: 0.3 }}
                transition={{ duration: 0.5, delay: 0.18, ease: EASE }}
                className="card-edged rounded-2xl bg-surface p-4 sm:p-5 ring-1 ring-line/80 transition-all hover:ring-gold-deep/30"
              >
                <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
                  <div className="flex items-center gap-3.5">
                    <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-gold-pale/60 text-gold-deep">
                      <MapPin weight="bold" className="h-5 w-5" />
                    </span>
                    <div>
                      <p className="text-xs font-semibold uppercase tracking-wider text-ink-soft">
                        Sede y Cobertura
                      </p>
                      <p className="mt-0.5 text-base sm:text-lg font-bold text-ink">
                        {siteConfig.ciudad}
                      </p>
                      <p className="text-xs text-ink-soft">Atención presencial en Bogotá y virtual en toda Colombia</p>
                    </div>
                  </div>

                  <a
                    href={`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(siteConfig.ciudad)}`}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="inline-flex items-center gap-1.5 self-end sm:self-center rounded-full border border-line bg-surface px-3.5 py-1.5 text-xs font-semibold text-ink transition hover:border-gold-deep hover:text-gold-deep shrink-0"
                  >
                    <span>Ver mapa</span>
                    <ArrowSquareOut className="h-3.5 w-3.5" weight="bold" />
                  </a>
                </div>
              </motion.div>

              {/* 5. Portal de Seguimiento Procesal */}
              <motion.div
                initial={{ opacity: 0, y: 16 }}
                whileInView={{ opacity: 1, y: 0 }}
                viewport={{ once: true, amount: 0.3 }}
                transition={{ duration: 0.5, delay: 0.24, ease: EASE }}
                className="card-edged rounded-2xl bg-gradient-to-r from-gold-pale/30 via-surface to-surface p-4 sm:p-5 ring-1 ring-gold-deep/30 transition-all hover:ring-gold-deep/60"
              >
                <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
                  <div className="flex items-center gap-3.5">
                    <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-gold-pale text-gold-deep">
                      <FileMagnifyingGlass weight="bold" className="h-5 w-5" />
                    </span>
                    <div>
                      <div className="flex items-center gap-2">
                        <p className="text-xs font-semibold uppercase tracking-wider text-gold-deep">
                          Seguimiento Procesal 24/7
                        </p>
                        <span className="rounded-full bg-gold-pale px-2 py-0.5 text-[10px] font-semibold text-gold-deep">
                          Clientes SIE
                        </span>
                      </div>
                      <p className="mt-0.5 text-base font-bold text-ink">
                        Consulta el estado en vivo de tu caso
                      </p>
                      <p className="text-xs text-ink-soft">Revisa radicados, providencias y actuaciones judiciales</p>
                    </div>
                  </div>

                  <Link
                    href="/consulta-caso"
                    className="inline-flex items-center gap-1.5 self-end sm:self-center rounded-full bg-gold-deep px-4 py-2 text-xs font-semibold text-white shadow-sm transition hover:bg-gold shrink-0"
                  >
                    <span>Consultar caso</span>
                    <ArrowSquareOut className="h-3.5 w-3.5" weight="bold" />
                  </Link>
                </div>
              </motion.div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
