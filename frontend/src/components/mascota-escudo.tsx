"use client";

import { useId } from "react";

// Mascota propia, ilustrada a mano en SVG: esta app no tiene generación de
// imágenes disponible, así que en vez de imitar el estilo fotorrealista
// (y las marcas de terceros) de cuidatumarca.siejuridicos.com, se creó un
// personaje vectorial propio con la misma intención tierna/cercana. Es un
// escudo con cara: alude directamente al concepto de "proteger tu marca"
// sin depender de ningún archivo externo ni de la identidad de nadie más.
// Tres variantes de la misma mascota (saludo, feliz, preocupada) para no
// tener que dibujar un personaje distinto por sección.

const CARA = "#1c1811";

function Cuerpo({ gradId, muted = false }: { gradId: string; muted?: boolean }) {
  return (
    <>
      <defs>
        <linearGradient id={gradId} x1="0%" y1="0%" x2="100%" y2="100%">
          {muted ? (
            <>
              <stop offset="0%" stopColor="#6b5c50" />
              <stop offset="100%" stopColor="#332a22" />
            </>
          ) : (
            <>
              <stop offset="0%" stopColor="#f1cf6a" />
              <stop offset="55%" stopColor="#d9a925" />
              <stop offset="100%" stopColor="#a97c16" />
            </>
          )}
        </linearGradient>
      </defs>
      <path
        d="M0,-70 C-48,-70 -52,-46 -52,-22 C-52,14 -26,58 0,78 C26,58 52,14 52,-22 C52,-46 48,-70 0,-70 Z"
        fill={`url(#${gradId})`}
      />
    </>
  );
}

function Estrella({ x, y, s = 1 }: { x: number; y: number; s?: number }) {
  return (
    <path
      d={`M${x},${y - 8 * s} L${x + 2.4 * s},${y - 2.4 * s} L${x + 8 * s},${y} L${x + 2.4 * s},${y + 2.4 * s} L${x},${y + 8 * s} L${x - 2.4 * s},${y + 2.4 * s} L${x - 8 * s},${y} L${x - 2.4 * s},${y - 2.4 * s} Z`}
      fill="#f1cf6a"
      opacity={0.85}
    />
  );
}

function Rubor() {
  return (
    <>
      <ellipse cx={-28} cy={8} rx={9} ry={5} fill="#b8523f" opacity={0.3} />
      <ellipse cx={28} cy={8} rx={9} ry={5} fill="#b8523f" opacity={0.3} />
    </>
  );
}

function OjosFelices() {
  return (
    <>
      <circle cx={-17} cy={-14} r={8} fill={CARA} />
      <circle cx={17} cy={-14} r={8} fill={CARA} />
      <circle cx={-14} cy={-17} r={2.4} fill="#fff" />
      <circle cx={20} cy={-17} r={2.4} fill="#fff" />
    </>
  );
}

function SonrisaFeliz() {
  return <path d="M-14,14 Q0,26 14,14" stroke={CARA} strokeWidth={5} strokeLinecap="round" fill="none" />;
}

/** Mascota saludando, con un pergamino enrollado: para el sub-hero. */
export function MascotaSaludo({ className }: { className?: string }) {
  const gradId = useId();
  return (
    <svg viewBox="0 0 200 220" className={className} role="img" aria-label="Mascota de SIE Jurídicos saludando">
      <g transform="translate(100,112)">
        {/* brazo que saluda */}
        <path d="M40,-8 Q58,-34 66,-52" stroke="#a97c16" strokeWidth={15} strokeLinecap="round" fill="none" />
        <circle cx={66} cy={-52} r={9} fill="#a97c16" />
        <path d="M76,-62 Q82,-58 80,-50" stroke="#d9a925" strokeWidth={3} strokeLinecap="round" fill="none" opacity={0.7} />
        <path d="M82,-48 Q88,-46 86,-38" stroke="#d9a925" strokeWidth={3} strokeLinecap="round" fill="none" opacity={0.5} />

        {/* brazo que sostiene el pergamino */}
        <path d="M-40,-6 Q-56,14 -52,34" stroke="#a97c16" strokeWidth={15} strokeLinecap="round" fill="none" />
        <circle cx={-52} cy={34} r={9} fill="#a97c16" />
        <g transform="translate(-58,26) rotate(-24)">
          <rect x={-16} y={-7} width={32} height={14} rx={7} fill="#f1e2b8" />
          <circle cx={0} cy={0} r={4} fill="#b8523f" />
        </g>

        <Cuerpo gradId={gradId} />
        <Rubor />
        <OjosFelices />
        <SonrisaFeliz />
        <Estrella x={-70} y={-58} s={0.8} />
        <Estrella x={78} y={2} s={0.6} />
      </g>
    </svg>
  );
}

/** Mascota celebrando, brazos arriba: para la tarjeta "marca protegida". */
export function MascotaFeliz({ className }: { className?: string }) {
  const gradId = useId();
  return (
    <svg viewBox="0 0 200 220" className={className} role="img" aria-label="Mascota de SIE Jurídicos celebrando">
      <g transform="translate(100,118)">
        <path d="M-40,-8 Q-60,-30 -68,-54" stroke="#a97c16" strokeWidth={15} strokeLinecap="round" fill="none" />
        <circle cx={-68} cy={-54} r={9} fill="#a97c16" />
        <path d="M40,-8 Q60,-30 68,-54" stroke="#a97c16" strokeWidth={15} strokeLinecap="round" fill="none" />
        <circle cx={68} cy={-54} r={9} fill="#a97c16" />

        <Cuerpo gradId={gradId} />
        <Rubor />
        <OjosFelices />
        <SonrisaFeliz />

        <Estrella x={-86} y={-70} s={0.9} />
        <Estrella x={90} y={-64} s={0.7} />
        <Estrella x={0} y={-96} s={0.6} />
      </g>
    </svg>
  );
}

/** Mascota preocupada con una caja vacía: para "marca no protegida". */
export function MascotaPreocupada({ className }: { className?: string }) {
  const gradId = useId();
  return (
    <svg viewBox="0 0 200 220" className={className} role="img" aria-label="Mascota de SIE Jurídicos preocupada, sosteniendo una caja vacía">
      <g transform="translate(100,110)">
        <path d="M-38,-2 Q-46,20 -34,38" stroke="#5c4c40" strokeWidth={15} strokeLinecap="round" fill="none" />
        <path d="M38,-2 Q46,20 34,38" stroke="#5c4c40" strokeWidth={15} strokeLinecap="round" fill="none" />

        <Cuerpo gradId={gradId} muted />

        <path d="M-26,-28 L-9,-22" stroke={CARA} strokeWidth={3.5} strokeLinecap="round" />
        <path d="M26,-28 L9,-22" stroke={CARA} strokeWidth={3.5} strokeLinecap="round" />
        <circle cx={-17} cy={-12} r={6} fill={CARA} />
        <circle cx={17} cy={-12} r={6} fill={CARA} />
        <path d="M-11,22 Q0,13 11,22" stroke={CARA} strokeWidth={5} strokeLinecap="round" fill="none" />

        <path
          d="M-32,40 L32,40 L38,66 L-38,66 Z"
          fill="none"
          stroke="#8a7a6a"
          strokeWidth={3}
          strokeLinejoin="round"
        />
        <path d="M-38,66 L-30,52 M38,66 L30,52" stroke="#6b5c50" strokeWidth={2} opacity={0.6} />
      </g>
    </svg>
  );
}
