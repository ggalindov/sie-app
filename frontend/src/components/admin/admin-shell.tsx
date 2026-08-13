"use client";

import Image from "next/image";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";
import {
  ChartBar,
  Envelope,
  Article,
  Users,
  SignOut,
  List,
  X,
  UserCircle,
  Megaphone,
  Quotes,
} from "@phosphor-icons/react";
import { useAuth } from "@/lib/auth-context";

const navItems = [
  { href: "/admin", label: "Resumen", icon: ChartBar, roles: ["ADMIN_GENERAL", "ABOGADO"] },
  { href: "/admin/solicitudes", label: "Solicitudes", icon: Envelope, roles: ["ADMIN_GENERAL", "ABOGADO"] },
  { href: "/admin/articulos", label: "Blog", icon: Article, roles: ["ADMIN_GENERAL", "ABOGADO"] },
  { href: "/admin/testimonios", label: "Testimonios", icon: Quotes, roles: ["ADMIN_GENERAL", "ABOGADO"] },
  { href: "/admin/marketing", label: "Marketing", icon: Megaphone, roles: ["ADMIN_GENERAL", "ABOGADO"] },
  { href: "/admin/usuarios", label: "Usuarios", icon: Users, roles: ["ADMIN_GENERAL"] },
] as const;

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
      <div className="flex min-h-screen items-center justify-center bg-paper text-sm text-ink-soft">
        Cargando...
      </div>
    );
  }

  const items = navItems.filter((item) => (item.roles as readonly string[]).includes(sesion.rol));

  return (
    <div className="flex min-h-screen bg-paper">
      {/* Sidebar desktop */}
      <aside className="hidden w-64 shrink-0 flex-col border-r border-line bg-surface lg:flex">
        <SidebarContent items={items} pathname={pathname} sesion={sesion} onLogout={logout} />
      </aside>

      {/* Sidebar móvil */}
      {menuAbierto && (
        <div className="fixed inset-0 z-50 flex lg:hidden">
          <div className="w-72 flex-col border-r border-line bg-surface flex">
            <SidebarContent items={items} pathname={pathname} sesion={sesion} onLogout={logout} />
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
  items,
  pathname,
  sesion,
  onLogout,
}: {
  items: typeof navItems extends readonly (infer T)[] ? T[] : never;
  pathname: string | null;
  sesion: { nombre: string; rol: string };
  onLogout: () => void;
}) {
  return (
    <>
      <div className="flex h-16 shrink-0 items-center gap-2 border-b border-line px-5">
        <Image src="/marca/logo.png" alt="" width={30} height={30} className="h-[30px] w-[30px] object-contain" />
        <span className="font-display text-base font-semibold">Panel</span>
      </div>

      <nav className="flex-1 space-y-1 px-3 py-5">
        {items.map((item) => {
          const activo = pathname === item.href || (item.href !== "/admin" && pathname?.startsWith(item.href));
          const Icon = item.icon;
          return (
            <Link
              key={item.href}
              href={item.href}
              className={`flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm transition-colors ${
                activo ? "bg-gold-pale/50 font-medium text-ink" : "text-ink-soft hover:bg-ink/5"
              }`}
            >
              <Icon weight={activo ? "fill" : "light"} className="h-5 w-5 shrink-0" />
              {item.label}
            </Link>
          );
        })}
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
