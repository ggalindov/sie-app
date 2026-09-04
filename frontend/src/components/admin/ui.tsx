import type { ButtonHTMLAttributes, ReactNode } from "react";
import { cn } from "@/lib/utils";

export function AdminPageHeader({
  title,
  description,
  action,
}: {
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <div className="relative mb-9 flex flex-col gap-4 overflow-hidden rounded-3xl bg-surface px-6 py-7 ring-1 ring-line sm:flex-row sm:items-end sm:justify-between sm:px-8">
      {/* Cabecera con peso propio en vez de un <h1> suelto sobre el fondo de la página,
          pero siempre en paleta clara (el panel no lleva fondos oscuros, a pedido
          explícito del usuario): un halo dorado suave sobre superficie clara, con el
          mismo filo superior en degradado que ya usa .card-edged del sitio público. */}
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-0"
        style={{
          backgroundImage:
            "radial-gradient(480px circle at 10% -10%, rgba(217,169,37,0.14), transparent 60%), radial-gradient(360px circle at 95% 110%, rgba(217,169,37,0.08), transparent 60%)",
        }}
      />
      <div
        aria-hidden="true"
        className="absolute inset-x-0 top-0 h-[3px]"
        style={{ backgroundImage: "linear-gradient(90deg, var(--color-gold-deep), var(--color-gold) 55%, var(--color-gold-pale))" }}
      />
      <div className="relative">
        <h1 className="font-display text-2xl tracking-tight text-ink sm:text-3xl">{title}</h1>
        {description && <p className="mt-1.5 text-sm text-ink-soft">{description}</p>}
      </div>
      {action && <div className="relative">{action}</div>}
    </div>
  );
}

const badgeTones = {
  neutral: "bg-ink/8 text-ink-soft",
  gold: "bg-gold-pale/60 text-gold-deep",
  success: "bg-emerald-100 text-emerald-800",
  warning: "bg-amber-100 text-amber-800",
  danger: "bg-red-100 text-red-800",
} as const;

const badgeDot = {
  neutral: "bg-ink-soft",
  gold: "bg-gold-deep",
  success: "bg-emerald-600",
  warning: "bg-amber-600",
  danger: "bg-red-600",
} as const;

export function Badge({
  children,
  tone = "neutral",
}: {
  children: ReactNode;
  tone?: keyof typeof badgeTones;
}) {
  return (
    <span className={cn("inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium", badgeTones[tone])}>
      <span className={cn("h-1.5 w-1.5 shrink-0 rounded-full", badgeDot[tone])} />
      {children}
    </span>
  );
}

// Paleta de las píldoras de notificación (correo/WhatsApp enviados o pendientes, estado de
// pago, respuesta del cliente): más grandes y con más peso visual que Badge a propósito
// -- son el primer dato que un admin necesita ver de un vistazo en Casos y Cobros
// Pendientes ("qué falta por notificar"), así que se les da jerarquía propia en vez de
// competir en tamaño con etiquetas secundarias como el número de caso o la fuente.
const notificationTones = {
  success: {
    wrap: "bg-emerald-50 text-emerald-800 ring-1 ring-emerald-200",
    iconWrap: "bg-emerald-600 text-white",
  },
  warning: {
    wrap: "bg-amber-50 text-amber-900 ring-1 ring-amber-200",
    iconWrap: "bg-amber-500 text-white",
  },
  danger: {
    wrap: "bg-red-50 text-red-800 ring-1 ring-red-200",
    iconWrap: "bg-red-600 text-white",
  },
  neutral: {
    wrap: "bg-ink/[0.04] text-ink-soft ring-1 ring-ink/10",
    iconWrap: "bg-ink/12 text-ink-soft",
  },
} as const;

export function NotificationBadge({
  children,
  tone = "neutral",
  icon,
}: {
  children: ReactNode;
  tone?: keyof typeof notificationTones;
  icon: ReactNode;
}) {
  const t = notificationTones[tone];
  return (
    <span className={cn("inline-flex items-center gap-2.5 rounded-full py-1.5 pl-1.5 pr-4 text-sm font-semibold", t.wrap)}>
      <span className={cn("flex h-7 w-7 shrink-0 items-center justify-center rounded-full", t.iconWrap)}>
        {icon}
      </span>
      {children}
    </span>
  );
}

export function AdminButton({
  variant = "primary",
  className,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: "primary" | "secondary" | "ghost" | "danger";
}) {
  const base =
    "relative inline-flex items-center justify-center gap-2 rounded-lg px-5 py-2.5 text-sm font-medium transition-all active:scale-[0.98] disabled:opacity-50 disabled:active:scale-100";
  const variants = {
    // cta-boton (globals.css): mismo filo desplazado tipo sello que ya usa el sitio
    // público, en vez de la píldora + insignia circular genérica de antes.
    primary: "cta-boton bg-gold text-ink-fixed shadow-[0_10px_24px_-12px_rgba(169,124,22,0.6)]",
    secondary: "bg-ink text-paper hover:bg-ink/85",
    ghost: "bg-ink/5 text-ink hover:bg-ink/10",
    danger: "bg-red-50 text-red-700 hover:bg-red-100",
  };
  return <button className={cn(base, variants[variant], className)} {...props} />;
}

export function AdminCard({
  children,
  className,
  accent,
}: {
  children: ReactNode;
  className?: string;
  /** Filo superior en degradado dorado (mismo motivo que .card-edged del sitio público),
      para dar jerarquía a las tarjetas que de verdad son protagonistas de la página. */
  accent?: boolean;
}) {
  return (
    <div className={cn("rounded-2xl bg-surface p-6 ring-1 ring-line", accent && "card-edged", className)}>
      {children}
    </div>
  );
}

// Loader de marca del panel, reemplaza el "Cargando..." de texto plano que tenía cada
// pantalla -- pedido explícito del usuario de animaciones de carga "únicas y hermosas" en
// vez del texto simple de antes. Dos anillos concéntricos girando en sentidos y velocidades
// distintas (ver globals.css: admin-loader-anillo / admin-loader-anillo-interno), en dorado
// de marca, con curvas de aceleración propias en vez de un giro lineal genérico.
export function AdminLoader({
  label = "Cargando",
  size = "md",
  className,
}: {
  /** null oculta la etiqueta -- útil para el uso "sm" incrustado junto a otro texto. */
  label?: string | null;
  size?: "sm" | "md";
  className?: string;
}) {
  const dimension = size === "sm" ? "h-5 w-5" : "h-11 w-11";
  // className, cuando se pasa, reemplaza el padding por defecto en vez de sumarse (dos
  // clases py-* de Tailwind conviviendo en el mismo elemento es orden-dependiente e
  // impredecible) -- así cada pantalla puede ajustar el espaciado sin pelear contra el
  // valor por defecto pensado para el uso de página completa.
  const padding = className ?? (size === "md" ? "py-16" : "");
  return (
    <div className={cn("flex flex-col items-center justify-center gap-3", padding)}>
      <span className={cn("relative inline-flex shrink-0", dimension)} role="status" aria-label={label ?? "Cargando"}>
        <span className="absolute inset-0 rounded-full border-2 border-gold-pale/60" />
        <span className="admin-loader-anillo absolute inset-0 rounded-full border-2 border-transparent border-t-gold border-r-gold-deep" />
        <span className="admin-loader-anillo-interno absolute inset-[3px] rounded-full border-2 border-transparent border-b-gold-deep/70" />
      </span>
      {label && <span className="text-[11px] font-medium uppercase tracking-[0.16em] text-ink-soft/60">{label}</span>}
    </div>
  );
}

export function EmptyState({ title, description }: { title: string; description?: string }) {
  return (
    <div className="flex flex-col items-center justify-center rounded-2xl border border-dashed border-line bg-[radial-gradient(circle_at_50%_0%,rgba(217,169,37,0.06),transparent_65%)] py-16 text-center">
      <span className="flex h-12 w-12 items-center justify-center rounded-full bg-gold-pale/50 text-gold-deep">
        <svg viewBox="0 0 24 24" fill="none" className="h-5 w-5" aria-hidden="true">
          <path
            d="M4 7h16M4 12h10M4 17h13"
            stroke="currentColor"
            strokeWidth="1.6"
            strokeLinecap="round"
          />
        </svg>
      </span>
      <p className="mt-4 text-sm font-medium text-ink">{title}</p>
      {description && <p className="mt-1 max-w-xs text-sm text-ink-soft">{description}</p>}
    </div>
  );
}
