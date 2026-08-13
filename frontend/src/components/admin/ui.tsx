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
    <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div>
        <h1 className="font-display text-2xl tracking-tight text-ink sm:text-3xl">{title}</h1>
        {description && <p className="mt-1 text-sm text-ink-soft">{description}</p>}
      </div>
      {action}
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

export function Badge({
  children,
  tone = "neutral",
}: {
  children: ReactNode;
  tone?: keyof typeof badgeTones;
}) {
  return (
    <span className={cn("inline-flex items-center rounded-full px-2.5 py-1 text-xs font-medium", badgeTones[tone])}>
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
  const base = "inline-flex items-center justify-center gap-2 rounded-full px-5 py-2.5 text-sm font-medium transition-opacity disabled:opacity-50";
  const variants = {
    primary: "bg-gold text-ink",
    secondary: "bg-ink text-paper",
    ghost: "bg-ink/5 text-ink hover:bg-ink/10",
    danger: "bg-red-50 text-red-700 hover:bg-red-100",
  };
  return <button className={cn(base, variants[variant], className)} {...props} />;
}

export function AdminCard({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <div className={cn("rounded-2xl bg-surface p-6 ring-1 ring-line", className)}>
      {children}
    </div>
  );
}

export function EmptyState({ title, description }: { title: string; description?: string }) {
  return (
    <div className="flex flex-col items-center justify-center rounded-2xl border border-dashed border-line py-16 text-center">
      <p className="text-sm font-medium text-ink">{title}</p>
      {description && <p className="mt-1 max-w-xs text-sm text-ink-soft">{description}</p>}
    </div>
  );
}
