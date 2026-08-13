"use client";

import Image from "next/image";
import Link from "next/link";
import { motion } from "motion/react";
import { ArrowRight } from "@phosphor-icons/react";

const EASE = [0.16, 1, 0.3, 1] as const;

export function TalentoHumano() {
  return (
    <section className="section-seam py-24 md:py-32">
      <div className="mx-auto grid max-w-7xl items-center gap-12 px-6 md:grid-cols-2 md:gap-16">
        <motion.div
          initial={{ opacity: 0, y: 24 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, amount: 0.4 }}
          transition={{ duration: 0.7, ease: EASE }}
          className="order-2 aspect-[4/3] overflow-hidden rounded-3xl md:order-1"
        >
          <Image
            src="/marca/hero-building.jpg"
            alt="Edificio corporativo"
            width={800}
            height={600}
            className="h-full w-full object-cover"
          />
        </motion.div>

        <motion.div
          initial={{ opacity: 0, y: 24 }}
          whileInView={{ opacity: 1, y: 0 }}
          viewport={{ once: true, amount: 0.4 }}
          transition={{ duration: 0.7, delay: 0.1, ease: EASE }}
          className="order-1 md:order-2"
        >
          <h2 className="font-display text-3xl leading-tight tracking-tight md:text-4xl">
            Impulsa tu empresa con el talento ideal.
          </h2>
          <p className="mt-5 max-w-md text-base leading-relaxed text-ink-soft">
            Buscamos profesionales que se alineen con la cultura y los
            objetivos de tu empresa: ejecutivos, equipos administrativos,
            técnicos o personal operativo, en el tiempo que necesites.
          </p>
          <Link
            href="#contacto"
            className="group mt-7 inline-flex items-center gap-3 rounded-full bg-ink py-3.5 pl-7 pr-3 text-sm font-medium text-paper transition-transform duration-300 active:scale-[0.98]"
          >
            Solicitar información
            <span className="flex h-8 w-8 items-center justify-center rounded-full bg-paper/15 transition-transform duration-300 group-hover:translate-x-0.5">
              <ArrowRight className="h-4 w-4" weight="bold" />
            </span>
          </Link>
        </motion.div>
      </div>
    </section>
  );
}
