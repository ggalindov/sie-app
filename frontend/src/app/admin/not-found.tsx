import Link from "next/link";
import { AdminButton } from "@/components/admin/ui";

// 404 propio del panel: fuera de (dashboard), así que no lleva el sidebar
// (una URL de admin rota no garantiza que haya sesión válida para armarlo).
// Estilo siempre claro y funcional, igual que el resto de /admin/**.
export default function AdminNotFound() {
  return (
    <div className="flex min-h-screen items-center justify-center bg-paper px-6">
      <div className="max-w-sm text-center">
        <p className="font-display text-5xl text-ink">404</p>
        <p className="mt-3 text-sm font-medium text-ink">Esta página del panel no existe.</p>
        <p className="mt-1 text-sm text-ink-soft">
          Revisa la URL o vuelve al panel de administración.
        </p>
        <Link href="/admin" className="mt-6 inline-block">
          <AdminButton>Ir al panel</AdminButton>
        </Link>
      </div>
    </div>
  );
}
