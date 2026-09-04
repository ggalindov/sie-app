"use client";

import Image from "next/image";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";
import {
  ChartBar,
  ChartLineUp,
  Envelope,
  Article,
  Users,
  SignOut,
  List,
  X,
  UserCircle,
  Megaphone,
  Quotes,
  Question,
  Briefcase,
  CurrencyCircleDollar,
  ClockCounterClockwise,
} from "@phosphor-icons/react";
import { useAuth } from "@/lib/auth-context";
import { AdminLoader } from "@/components/admin/ui";

type Rol = "ADMIN_GENERAL" | "ABOGADO";

type NavItem = {
  href: string;
  label: string;
  icon: typeof ChartBar;
  roles: Rol[];
};

type NavGroup = {
  titulo: string;
  items: NavItem[];
};

// Agrupado por función (no por rol): un mismo grupo puede mezclar ítems visibles para
// ambos roles con ítems solo-admin, el filtrado por rol pasa primero y un grupo que
// quede vacío después de filtrar simplemente no se renderiza (ver AdminShell.grupos).
const navGroups: NavGroup[] = [
  {
    titulo: "General",
    items: [
      { href: "/admin", label: "Resumen", icon: ChartBar, roles: ["ADMIN_GENERAL", "ABOGADO"] },
      { href: "/admin/estadisticas", label: "Estadísticas", icon: ChartLineUp, roles: ["ADMIN_GENERAL", "ABOGADO"] },
    ],
  },
  {
    titulo: "Gestión",
    items: [
      { href: "/admin/solicitudes", label: "Solicitudes", icon: Envelope, roles: ["ADMIN_GENERAL", "ABOGADO"] },
      { href: "/admin/casos", label: "Casos", icon: Briefcase, roles: ["ADMIN_GENERAL", "ABOGADO"] },
      { href: "/admin/cobros", label: "Cobros Pendientes", icon: CurrencyCircleDollar, roles: ["ADMIN_GENERAL"] },
    ],
  },
  {
    titulo: "Contenido",
    items: [
      { href: "/admin/articulos", label: "Blog y Noticias", icon: Article, roles: ["ADMIN_GENERAL", "ABOGADO"] },
      { href: "/admin/faq", label: "Preguntas frecuentes", icon: Question, roles: ["ADMIN_GENERAL", "ABOGADO"] },
      { href: "/admin/testimonios", label: "Testimonios", icon: Quotes, roles: ["ADMIN_GENERAL"] },
    ],
  },
  {
    titulo: "Administración",
    items: [
      { href: "/admin/marketing", label: "Marketing", icon: Megaphone, roles: ["ADMIN_GENERAL"] },
      { href: "/admin/usuarios", label: "Usuarios", icon: Users, roles: ["ADMIN_GENERAL"] },
      { href: "/admin/registro", label: "Registro del sistema", icon: ClockCounterClockwise, roles: ["ADMIN_GENERAL"] },
    ],
  },
];

export function AdminShell({ children }: { children: ReactNode }) {
  const { sesion, cargando, logout } = useAuth();
  const router = useRouter();
  const pathname = usePathname();
  const [menuAbierto, setMenuAbierto] = useState(false);

  useEffect(() => {
    if (!cargando && !sesion) {
      router.replace("/admin/login");
    }
  }, [cargando, sesion, router]);

  useEffect(() => {
    setMenuAbierto(false);
  }, [pathname]);

  if (cargando || !sesion) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-paper">
        <AdminLoader />
      </div>
    );
  }

  const grupos = navGroups
    .map((grupo) => ({
      titulo: grupo.titulo,
      items: grupo.items.filter((item) => (item.roles as readonly string[]).includes(sesion.rol)),
    }))
    // un grupo entero (ej. "Administración") puede quedar sin ítems para el rol ABOGADO:
    // se descarta en vez de renderizar un título de sección vacío
    .filter((grupo) => grupo.items.length > 0);

  return (
    <div className="flex min-h-screen bg-paper">
      {/* Sidebar desktop */}
      <aside className="hidden w-64 shrink-0 flex-col border-r border-line bg-surface lg:flex">
        <SidebarContent grupos={grupos} pathname={pathname} sesion={sesion} onLogout={logout} />
      </aside>

      {/* Sidebar móvil */}
      {menuAbierto && (
        <div className="fixed inset-0 z-50 flex lg:hidden">
          <div className="w-72 flex-col border-r border-line bg-surface flex">
            <SidebarContent grupos={grupos} pathname={pathname} sesion={sesion} onLogout={logout} />
          </div>
          <button
            type="button"
            aria-label="Cerrar menú"
            onClick={() => setMenuAbierto(false)}
            className="flex-1 bg-ink/40"
          />
        </div>
      )}

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex h-16 shrink-0 items-center justify-between border-b border-line bg-surface px-4 lg:hidden">
          <div className="flex items-center gap-2">
            <Image src="/marca/logo.png" alt="" width={28} height={28} className="h-7 w-7 object-contain" />
            <span className="font-display text-sm font-semibold">Panel</span>
          </div>
          <button
            type="button"
            aria-label="Abrir menú"
            onClick={() => setMenuAbierto((v) => !v)}
            className="flex h-10 w-10 items-center justify-center rounded-full text-ink"
          >
            {menuAbierto ? <X className="h-5 w-5" /> : <List className="h-5 w-5" />}
          </button>
        </header>

        <main className="min-w-0 flex-1 overflow-x-hidden px-4 py-8 sm:px-8 lg:py-10">
          {children}
        </main>
      </div>
    </div>
  );
}

function SidebarContent({
  grupos,
  pathname,
  sesion,
  onLogout,
}: {
  grupos: NavGroup[];
  pathname: string | null;
  sesion: { nombre: string; rol: string };
  onLogout: () => void;
}) {
  return (
    <>
      <div className="relative flex h-16 shrink-0 items-center gap-2.5 overflow-hidden border-b border-line bg-surface px-5">
        <div
          aria-hidden="true"
          className="pointer-events-none absolute inset-0"
          style={{ backgroundImage: "radial-gradient(220px circle at 0% 0%, rgba(217,169,37,0.14), transparent 65%)" }}
        />
        <span className="relative flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-gold-pale/50 ring-1 ring-gold/25">
          <Image src="/marca/logo.png" alt="" width={22} height={22} className="h-[22px] w-[22px] object-contain" />
        </span>
        <span className="relative font-display text-base font-semibold text-ink">Panel</span>
      </div>

      <nav className="flex-1 overflow-y-auto px-3 py-5">
        {grupos.map((grupo, i) => (
          <div key={grupo.titulo} className={i > 0 ? "mt-5 border-t border-line pt-4" : ""}>
            <p className="px-3 pb-2 text-[11px] font-semibold uppercase tracking-[0.12em] text-ink-soft/60">
              {grupo.titulo}
            </p>
            <div className="space-y-1">
              {grupo.items.map((item) => {
                const activo = pathname === item.href || (item.href !== "/admin" && pathname?.startsWith(item.href));
                const Icon = item.icon;
                return (
                  <Link
                    key={item.href}
                    href={item.href}
                    className={`group relative flex items-center gap-3 rounded-xl py-2 pl-3 pr-3 text-sm transition-colors ${
                      activo ? "bg-gold-pale/40 font-medium text-ink" : "text-ink-soft hover:bg-ink/5"
                    }`}
                  >
                    {/* barra de acento a la izquierda: más claro que solo un fondo tenue,
                        y consistente con el filo dorado que ya usa el sitio público */}
                    <span
                      className={`absolute left-0 top-1/2 h-5 w-[3px] -translate-y-1/2 rounded-r-full bg-gold transition-transform duration-300 ${
                        activo ? "scale-y-100" : "scale-y-0"
                      }`}
                    />
                    <span
                      className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-lg transition-colors ${
                        activo ? "bg-gold text-ink-fixed" : "bg-ink/5 text-ink-soft group-hover:bg-ink/10"
                      }`}
                    >
                      <Icon weight={activo ? "fill" : "light"} className="h-4 w-4" />
                    </span>
                    {item.label}
                  </Link>
                );
              })}
            </div>
          </div>
        ))}
      </nav>

      <div className="border-t border-line p-3">
        <Link
          href="/admin/perfil"
          className={`flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm transition-colors ${
            pathname === "/admin/perfil" ? "bg-gold-pale/50 font-medium text-ink" : "text-ink-soft hover:bg-ink/5"
          }`}
        >
          <UserCircle weight="light" className="h-5 w-5 shrink-0" />
          <span className="min-w-0 flex-1 truncate">{sesion.nombre}</span>
        </Link>
        <p className="mt-1 truncate px-3 text-xs text-ink-soft">
          {sesion.rol === "ADMIN_GENERAL" ? "Administrador General" : "Abogado"}
        </p>
        <button
          type="button"
          onClick={onLogout}
          className="mt-2 flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-sm text-ink-soft transition-colors hover:bg-ink/5"
        >
          <SignOut weight="light" className="h-5 w-5 shrink-0" />
          Cerrar sesión
        </button>
      </div>
    </>
  );
}
