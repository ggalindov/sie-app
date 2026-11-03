"use client";

import { useEffect } from "react";
import { AdminButton } from "@/components/admin/ui";

// Error boundary propio del panel: paleta siempre clara, sin depender de
// SiteChrome/AdminShell (si el error viene de más arriba en el árbol, mejor
// no reutilizar piezas que podrían estar involucradas en la falla).
export default function AdminError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error("Error de página en el panel administrativo:", error);
  }, [error]);

  function onCerrarSesion() {
    try {
      localStorage.removeItem("sie_admin_token");
    } catch {
      // Ignora error si localStorage no está accesible
    }
    window.location.href = "/admin/login";
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-paper px-6">
      <div className="max-w-md text-center">
        <p className="font-display text-2xl text-ink">Algo no salió bien</p>
        <p className="mt-2 text-sm text-ink-soft">
          Hubo un problema mostrando esta sección del panel. Intenta de nuevo o vuelve a iniciar sesión.
        </p>
        <div className="mt-6 flex flex-col items-center justify-center gap-3 sm:flex-row">
          <AdminButton onClick={() => reset()}>Intentar de nuevo</AdminButton>
          <button
            type="button"
            onClick={onCerrarSesion}
            className="rounded-full border border-line px-5 py-2 text-sm font-medium text-ink transition-colors hover:bg-black/5"
          >
            Volver al inicio de sesión
          </button>
        </div>
      </div>
    </div>
  );
}
