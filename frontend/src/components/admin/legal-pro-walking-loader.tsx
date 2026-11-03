"use client";

import { useEffect, useState } from "react";
import { Sparkle, Buildings, ShieldCheck, Database } from "@phosphor-icons/react";

interface LegalProWalkingLoaderProps {
  titulo?: string;
  subtitulo?: string;
  className?: string;
}

const ETAPAS_AUDITORIA = [
  { texto: "Iniciando conexión segura con Croma API y Rama Judicial...", icono: Database },
  { texto: "El auditor Legal-Pro avanza hacia los despachos judiciales...", icono: Buildings },
  { texto: "Rastreando expediente, radicados asociados y autos...", icono: Sparkle },
  { texto: "Examinando estados electrónicos, fijaciones y boletines...", icono: ShieldCheck },
  { texto: "Extrayendo directorio de correos oficiales del tribunal...", icono: Buildings },
  { texto: "Compilando dossier completo y resguardando en memoria...", icono: ShieldCheck },
];

export function LegalProWalkingLoader({
  titulo = "Buscando en Legal-Pro",
  subtitulo = "Consultando expediente, actuaciones, boletines y correos oficiales del despacho...",
  className = "",
}: LegalProWalkingLoaderProps) {
  const [progreso, setProgreso] = useState(12);
  const [etapaIndice, setEtapaIndice] = useState(0);

  useEffect(() => {
    // Simula avance orgánico no-lineal: arranca rápido, inspecciona pausadamente y remata
    const intervaloProgreso = setInterval(() => {
      setProgreso((prev) => {
        if (prev < 35) return prev + 3;
        if (prev < 65) return prev + 1.8;
        if (prev < 88) return prev + 0.9;
        if (prev < 96) return prev + 0.3;
        return 96; // Se sostiene cerca del final hasta que responda la API
      });
    }, 280);

    const intervaloEtapas = setInterval(() => {
      setEtapaIndice((prev) => (prev + 1) % ETAPAS_AUDITORIA.length);
    }, 2400);

    return () => {
      clearInterval(intervaloProgreso);
      clearInterval(intervaloEtapas);
    };
  }, []);

  const EtapaIcono = ETAPAS_AUDITORIA[etapaIndice].icono;

  return (
    <div className={`my-auto w-full max-w-lg mx-auto py-10 px-6 text-center select-none ${className}`}>
      <style jsx>{`
        @keyframes walk-bob {
          0%, 100% { transform: translateY(0px); }
          50% { transform: translateY(-3.5px); }
        }
        @keyframes walk-leg-left {
          0% { transform: rotate(-26deg); }
          50% { transform: rotate(26deg); }
          100% { transform: rotate(-26deg); }
        }
        @keyframes walk-leg-right {
          0% { transform: rotate(26deg); }
          50% { transform: rotate(-26deg); }
          100% { transform: rotate(26deg); }
        }
        @keyframes walk-arm-front {
          0% { transform: rotate(-22deg); }
          50% { transform: rotate(22deg); }
          100% { transform: rotate(-22deg); }
        }
        @keyframes walk-arm-back {
          0% { transform: rotate(20deg); }
          50% { transform: rotate(-20deg); }
          100% { transform: rotate(20deg); }
        }
        @keyframes briefcase-swing {
          0% { transform: rotate(-14deg); }
          50% { transform: rotate(14deg); }
          100% { transform: rotate(-14deg); }
        }
        @keyframes shimmer-track {
          0% { background-position: -200% 0; }
          100% { background-position: 200% 0; }
        }
        .anim-bob {
          animation: walk-bob 0.55s ease-in-out infinite;
        }
        .anim-leg-left {
          transform-origin: 16px 30px;
          animation: walk-leg-left 0.55s ease-in-out infinite;
        }
        .anim-leg-right {
          transform-origin: 22px 30px;
          animation: walk-leg-right 0.55s ease-in-out infinite;
        }
        .anim-arm-front {
          transform-origin: 14px 17px;
          animation: walk-arm-front 0.55s ease-in-out infinite;
        }
        .anim-arm-back {
          transform-origin: 22px 17px;
          animation: walk-arm-back 0.55s ease-in-out infinite;
        }
        .anim-briefcase {
          transform-origin: 10px 25px;
          animation: briefcase-swing 0.55s ease-in-out infinite;
        }
        .anim-shimmer {
          background-size: 200% 100%;
          animation: shimmer-track 2.4s linear infinite;
        }
      `}</style>

      {/* Título de la búsqueda */}
      <div className="flex items-center justify-center gap-2">
        <span className="flex h-6 w-6 items-center justify-center rounded-full bg-gold/15 text-gold">
          <Sparkle className="h-3.5 w-3.5" weight="fill" />
        </span>
        <h3 className="font-display text-base font-semibold text-ink sm:text-lg">{titulo}</h3>
      </div>
      <p className="mt-1 text-xs text-ink-soft max-w-sm mx-auto">{subtitulo}</p>

      {/* Contenedor de la pista de progreso con el caminante */}
      <div className="relative mt-8 mb-4 pt-12 pb-2">
        {/* Personita con maletín caminando sobre el riel */}
        <div
          className="absolute top-0 -translate-x-1/2 transition-[left] duration-300 ease-out will-change-transform pointer-events-none"
          style={{ left: `${Math.min(Math.max(progreso, 5), 95)}%` }}
        >
          {/* Sombra suave bajo sus pies */}
          <div className="absolute -bottom-1 left-1/2 -translate-x-1/2 h-1.5 w-7 rounded-full bg-gold/25 blur-[2px]" />

          {/* Personaje en SVG: Abogado con maletín */}
          <div className="anim-bob">
            <svg
              width="44"
              height="50"
              viewBox="0 0 44 50"
              fill="none"
              xmlns="http://www.w3.org/2000/svg"
              className="drop-shadow-sm"
            >
              {/* Pierna Trasera (izquierda) */}
              <g className="anim-leg-left">
                {/* Muslo y pierna */}
                <path
                  d="M17 30 L14 40 L11 48"
                  stroke="#1E232A"
                  strokeWidth="3.2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
                {/* Zapato formal negro */}
                <path d="M11 48 L7 48" stroke="#0E1013" strokeWidth="3.5" strokeLinecap="round" />
              </g>

              {/* Brazo Trasero */}
              <g className="anim-arm-back">
                <path
                  d="M22 17 L25 25 L27 30"
                  stroke="#2C323B"
                  strokeWidth="2.8"
                  strokeLinecap="round"
                />
              </g>

              {/* Torso / Traje formal */}
              {/* Abrigo / Traje */}
              <path
                d="M14 15 C14 13.5 17 12 21 12 C25 12 28 13.5 28 15 L26 30 C26 31 16 31 16 30 Z"
                fill="#181B20"
              />
              {/* Cuello de camisa blanca */}
              <path d="M19 12 L21 17 L23 12 Z" fill="#F8FAFC" />
              {/* Corbata dorada de marca */}
              <path d="M20.5 14 L21.5 14 L22 21 L21 23 L20 21 Z" fill="#D4AF37" />
              {/* Solapas de chaqueta */}
              <path
                d="M16 13 L19.5 20 M26 13 L22.5 20"
                stroke="#2B303A"
                strokeWidth="1.2"
                strokeLinecap="round"
              />

              {/* Cabeza del profesional */}
              <circle cx="21" cy="7.5" r="5" fill="#E2BA96" />
              {/* Cabello estilizado / peinado formal */}
              <path
                d="M16 7 C16 4 18 3 22 3 C25 3 26 4 26.5 6 C25 6 23 5.5 20 6.5 C18 7.5 17 8 16 8 Z"
                fill="#1A1C1E"
              />
              {/* Gafas doradas sutiles de jurista */}
              <path
                d="M21 7 L24.5 7 M22.5 7 A 1.8 1.8 0 0 1 25.5 7"
                stroke="#C5A880"
                strokeWidth="0.8"
                strokeLinecap="round"
              />

              {/* Pierna Delantera (derecha) */}
              <g className="anim-leg-right">
                <path
                  d="M21 30 L24 39 L27 48"
                  stroke="#181B20"
                  strokeWidth="3.2"
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
                {/* Zapato delantero */}
                <path d="M27 48 L32 48" stroke="#0E1013" strokeWidth="3.5" strokeLinecap="round" />
              </g>

              {/* Brazo Delantero con Maletín */}
              <g className="anim-arm-front">
                <path
                  d="M15 17 L12 25 L10 27"
                  stroke="#1E232A"
                  strokeWidth="2.8"
                  strokeLinecap="round"
                />

                {/* Maletín Ejecutivo / Portafolio */}
                <g className="anim-briefcase">
                  {/* Manija del maletín */}
                  <path
                    d="M7 27 C7 25 11 25 11 27"
                    stroke="#5A3A1A"
                    strokeWidth="1.6"
                    strokeLinecap="round"
                    fill="none"
                  />
                  {/* Cuerpo del maletín de cuero */}
                  <rect
                    x="4"
                    y="27"
                    width="12"
                    height="9.5"
                    rx="1.8"
                    fill="#6A3B18"
                    stroke="#4A250B"
                    strokeWidth="0.8"
                  />
                  {/* Correa y herraje dorado */}
                  <rect x="9" y="30.5" width="2" height="2" rx="0.4" fill="#E6C665" />
                  <line x1="4" y1="29.5" x2="16" y2="29.5" stroke="#4A250B" strokeWidth="0.6" />
                </g>
              </g>
            </svg>
          </div>
        </div>

        {/* Barra de Progreso (Riel) */}
        <div className="relative h-2.5 w-full overflow-hidden rounded-full bg-ink/10 ring-1 ring-black/5 dark:bg-paper/10">
          {/* Fondo animado shimmer */}
          <div
            className="h-full rounded-full bg-gradient-to-r from-gold-pale via-gold to-gold-deep anim-shimmer transition-[width] duration-300 ease-out"
            style={{ width: `${progreso}%` }}
          />
        </div>
      </div>

      {/* Porcentaje y Etapa en vivo */}
      <div className="flex items-center justify-between text-[11px] text-ink-soft">
        <span className="font-mono font-medium text-gold-deep">{Math.round(progreso)}% completado</span>
        <span className="inline-flex items-center gap-1.5 font-medium text-ink transition-opacity duration-300">
          <EtapaIcono className="h-3.5 w-3.5 text-gold animate-pulse" weight="bold" />
          {ETAPAS_AUDITORIA[etapaIndice].texto}
        </span>
      </div>
    </div>
  );
}
