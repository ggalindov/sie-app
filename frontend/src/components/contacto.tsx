"use client";

import { motion } from "motion/react";
import {
  EnvelopeSimple,
  FacebookLogo,
  InstagramLogo,
  LinkedinLogo,
  MapPin,
  Phone,
  ShieldCheck,
  WhatsappLogo,
} from "@phosphor-icons/react";
import { siteConfig } from "@/lib/site-config";

const EASE = [0.16, 1, 0.3, 1] as const;

const canales = [
  { icono: Phone, label: "Teléfono", valor: siteConfig.telefono, href: siteConfig.telefonoHref },
  { icono: EnvelopeSimple, label: "Correo", valor: siteConfig.correo, href: `mailto:${siteConfig.correo}` },
  {
    icono: MapPin,
    label: "Ubicación",
    valor: siteConfig.ciudad,
    href: `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(siteConfig.ciudad)}`,
  },
];

// Contacto es un directorio de medios oficiales, nada más: no comparte
// formulario con "Agendar asesoría" (esa es su propia sección, independiente
// y con su propio flujo, a pedido explícito del usuario).
export function Contacto() {
  return (
    <section id="contacto" className="snap-slide section-seam bg-surface/90 py-24 md:py-32">
      <div className="mx-auto max-w-7xl px-6">
        <div className="grid gap-14 md:grid-cols-12 md:gap-16">
          <motion.div
            initial={{ opacity: 0, y: 24 }}
            whileInView={{ opacity: 1, y: 0 }}
            viewport={{ once: true, amount: 0.3 }}
            transition={{ duration: 0.7, ease: EASE }}
            className="md:col-span-5"
          >
            <h2 className="font-display text-3xl leading-tight tracking-tight md:text-4xl">
              Contacto
            </h2>
            <p className="mt-4 max-w-md text-base leading-relaxed text-ink-soft">
              Estos son nuestros medios de contacto oficiales. Escríbenos
              directamente por cualquiera de ellos.
            </p>

            <div className="mt-8 flex items-center gap-3">
              <a
                href={siteConfig.redes.facebook}
                target="_blank"
                rel="noopener noreferrer"
                aria-label="Facebook"
                className="flex h-10 w-10 items-center justify-center rounded-full bg-ink/5 text-ink-soft transition-colors hover:bg-gold hover:text-ink-fixed"
              >
                <FacebookLogo weight="light" className="h-5 w-5" />
              </a>
              <a
                href={siteConfig.redes.instagram}
                target="_blank"
                rel="noopener noreferrer"
                aria-label="Instagram"
                className="flex h-10 w-10 items-center justify-center rounded-full bg-ink/5 text-ink-soft transition-colors hover:bg-gold hover:text-ink-fixed"
              >
                <InstagramLogo weight="light" className="h-5 w-5" />
              </a>
              <a
                href={siteConfig.redes.linkedin}
                target="_blank"
                rel="noopener noreferrer"
                aria-label="LinkedIn"
                className="flex h-10 w-10 items-center justify-center rounded-full bg-ink/5 text-ink-soft transition-colors hover:bg-gold hover:text-ink-fixed"
              >
                <LinkedinLogo weight="light" className="h-5 w-5" />
              </a>
              <a
                href={siteConfig.whatsapp}
                target="_blank"
                rel="noopener noreferrer"
                aria-label="WhatsApp"
                className="flex h-10 w-10 items-center justify-center rounded-full bg-ink/5 text-ink-soft transition-colors hover:bg-gold hover:text-ink-fixed"
              >
                <WhatsappLogo weight="light" className="h-5 w-5" />
              </a>
            </div>

            <div className="mt-8 rounded-2xl bg-ink/5 p-5">
              <div className="flex items-center gap-3">
                <ShieldCheck weight="light" className="h-5 w-5 shrink-0 text-gold-deep" />
                <p className="text-xs font-medium uppercase tracking-[0.08em] text-ink">
                  Protección de tus datos
                </p>
              </div>
              <ul className="mt-3 space-y-2 text-xs leading-relaxed text-ink-soft">
                <li>
                  Tratamiento de datos personales conforme a la Ley 1581 de 2012 (Habeas
                  Data): tu información solo se usa para dar respuesta a tu solicitud.
                </li>
                <li>Nunca cedemos tus datos a terceros sin tu autorización expresa.</li>
                <li>Toda la comunicación con este sitio viaja cifrada (HTTPS/TLS).</li>
                <li>
                  Puedes solicitar en cualquier momento acceso, corrección o eliminación
                  de tus datos escribiéndonos a {siteConfig.correo}.
                </li>
              </ul>
            </div>
          </motion.div>

          <div className="flex flex-col gap-4 md:col-span-7 md:self-center">
            {canales.map((canal, i) => {
              const Icono = canal.icono;
              return (
                <motion.div
                  key={canal.label}
                  initial={{ opacity: 0, y: 24 }}
                  whileInView={{ opacity: 1, y: 0 }}
                  viewport={{ once: true, amount: 0.4 }}
                  transition={{ duration: 0.6, delay: 0.1 * i, ease: EASE }}
                >
                  <a
                    href={canal.href}
                    target={canal.label === "Ubicación" ? "_blank" : undefined}
                    rel={canal.label === "Ubicación" ? "noopener noreferrer" : undefined}
                    className="group block"
                  >
                    <div className="flex items-center gap-1.5 rounded-[1.75rem] bg-ink/5 p-1.5 ring-1 ring-ink/5 transition-transform duration-300 group-hover:-translate-x-1">
                      <div className="flex w-full items-center gap-5 rounded-[1.4rem] bg-surface px-7 py-6 ring-1 ring-line">
                        <span className="flex h-14 w-14 shrink-0 items-center justify-center rounded-full bg-gold-pale/60 text-gold-deep transition-transform duration-300 group-hover:scale-105">
                          <Icono weight="light" className="h-6 w-6" />
                        </span>
                        <div>
                          <p className="text-xs font-medium uppercase tracking-[0.1em] text-ink-soft">
                            {canal.label}
                          </p>
                          <p className="mt-1 text-lg font-medium text-ink">{canal.valor}</p>
                        </div>
                      </div>
                    </div>
                  </a>
                </motion.div>
              );
            })}
          </div>
        </div>
      </div>
    </section>
  );
}
