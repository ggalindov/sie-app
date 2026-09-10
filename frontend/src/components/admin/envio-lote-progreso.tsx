"use client";

import { useEffect, useState } from "react";
import { Clock, ShieldCheck, WhatsappLogo, EnvelopeSimple } from "@phosphor-icons/react";
import { cn } from "@/lib/utils";

interface EnvioLoteProgresoProps {
  activo: boolean;
  titulo: string;
  descripcion?: string;
  className?: string;
}

const MENSAJES_FASE = [
  "Conectando de forma segura con Meta WhatsApp Cloud API y Gmail SMTP...",
  "Enviando notificaciones secuenciales con pausas de seguridad entre contactos...",
  "Aplicando reintentos ante posibles micro-cortes o fallos transitorios de red...",
  "Verificando confirmaciones de entrega y protegiendo el cupo diario de 250 mensajes...",
  "Actualizando estados en la base de datos y registrando eventos de auditoría...",
];

export function EnvioLoteProgreso({
  activo,
  titulo,
  descripcion,
  className,
}: EnvioLoteProgresoProps) {
  const [segundos, setSegundos] = useState(0);
  const [faseIndex, setFaseIndex] = useState(0);

  useEffect(() => {
    if (!activo) {
      setSegundos(0);
      setFaseIndex(0);
      return;
    }

    const timerInterval = setInterval(() => {
      setSegundos((prev) => prev + 1);
    }, 1000);

    const faseInterval = setInterval(() => {
      setFaseIndex((prev) => (prev + 1) % MENSAJES_FASE.length);
    }, 6000);

    return () => {
      clearInterval(timerInterval);
      clearInterval(faseInterval);
    };
  }, [activo]);

  if (!activo) return null;

  const minutos = Math.floor(segundos / 60);
  const segRestantes = segundos % 60;
  const tiempoFormateado = `${String(minutos).padStart(2, "0")}:${String(segRestantes).padStart(2, "0")}`;

  return (
    <div
      role="status"
      aria-live="polite"
      className={cn(
        "relative mb-6 overflow-hidden rounded-2xl border border-gold/40 bg-surface p-5 shadow-lg ring-1 ring-gold/20 sm:p-6",
        className,
      )}
    >
      {/* Fondo con halo dorado suave */}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0 bg-[radial-gradient(ellipse_at_top_right,rgba(217,169,37,0.12),transparent_65%)]"
      />

      <div className="relative flex flex-col gap-4">
        {/* Cabecera del progreso */}
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-3">
            {/* Beacon pulsante activo */}
            <span className="relative flex h-3.5 w-3.5">
              <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-gold opacity-75" />
              <span className="relative inline-flex h-3.5 w-3.5 rounded-full bg-gold-deep" />
            </span>
            <div>
              <h3 className="text-base font-semibold text-ink sm:text-lg">{titulo}</h3>
              {descripcion && <p className="text-xs text-ink-soft sm:text-sm">{descripcion}</p>}
            </div>
          </div>

          {/* Contador de tiempo */}
          <div className="flex items-center gap-2 rounded-full border border-line bg-paper/80 px-3.5 py-1.5 text-xs font-mono font-medium text-ink-soft">
            <Clock className="h-4 w-4 text-gold-deep animate-spin" style={{ animationDuration: "6s" }} />
            <span>Tiempo transcurrido: <strong className="text-ink">{tiempoFormateado}</strong></span>
          </div>
        </div>

        {/* Barra de progreso animada con shimmer continuo */}
        <div className="space-y-2">
          <div className="relative h-2.5 w-full overflow-hidden rounded-full bg-ink/5 ring-1 ring-line/70">
            <div className="animacion-progreso-shimmer absolute inset-y-0 w-2/3 rounded-full bg-gradient-to-r from-gold-pale via-gold to-gold-deep shadow-sm" />
          </div>
          <div className="flex items-center justify-between text-xs text-ink-soft">
            <span className="font-medium text-gold-deep transition-all duration-500">
              {MENSAJES_FASE[faseIndex]}
            </span>
            <span className="hidden sm:inline-block text-[11px] text-ink-soft/70">
              Pausas anti-bloqueo activas
            </span>
          </div>
        </div>

        {/* Píldoras informativas del envío */}
        <div className="flex flex-wrap items-center gap-2 pt-1 text-xs text-ink-soft">
          <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-50 px-2.5 py-1 font-medium text-emerald-800 ring-1 ring-emerald-200">
            <WhatsappLogo weight="bold" className="h-3.5 w-3.5 text-emerald-600" />
            WhatsApp Cloud API
          </span>
          <span className="inline-flex items-center gap-1.5 rounded-full bg-amber-50 px-2.5 py-1 font-medium text-amber-900 ring-1 ring-amber-200">
            <EnvelopeSimple weight="bold" className="h-3.5 w-3.5 text-amber-600" />
            Gmail SMTP
          </span>
          <span className="inline-flex items-center gap-1.5 rounded-full bg-ink/[0.04] px-2.5 py-1 font-medium text-ink-soft ring-1 ring-ink/10">
            <ShieldCheck weight="bold" className="h-3.5 w-3.5 text-gold-deep" />
            Límite protegido: 250/día (retoma automática al día siguiente)
          </span>
        </div>
      </div>
    </div>
  );
}
