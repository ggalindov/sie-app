"use client";

import { useState, type FormEvent } from "react";
import { toast } from "sonner";
import { cambiarContrasena, ApiError } from "@/lib/admin-api";
import { useAuth } from "@/lib/auth-context";
import { AdminPageHeader, AdminCard, AdminButton } from "@/components/admin/ui";
import { PasswordInput } from "@/components/admin/password-input";

export default function PerfilPage() {
  const { sesion } = useAuth();
  const [contrasenaActual, setContrasenaActual] = useState("");
  const [contrasenaNueva, setContrasenaNueva] = useState("");
  const [confirmacion, setConfirmacion] = useState("");
  const [enviando, setEnviando] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (contrasenaNueva.length < 8) {
      toast.error("La nueva contraseña debe tener al menos 8 caracteres.");
      return;
    }
    if (contrasenaNueva !== confirmacion) {
      toast.error("Las contraseñas no coinciden.");
      return;
    }
    setEnviando(true);
    try {
      await cambiarContrasena(contrasenaActual, contrasenaNueva);
      toast.success("Contraseña actualizada.");
      setContrasenaActual("");
      setContrasenaNueva("");
      setConfirmacion("");
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo cambiar la contraseña.");
    } finally {
      setEnviando(false);
    }
  }

  return (
    <div className="max-w-lg">
      <AdminPageHeader title="Mi perfil" />

      <AdminCard>
        <p className="text-sm font-medium text-ink">{sesion?.nombre}</p>
        <p className="text-sm text-ink-soft">{sesion?.correo}</p>
        <p className="mt-1 text-xs text-ink-soft">
          {sesion?.rol === "ADMIN_GENERAL" ? "Administrador General" : "Abogado"}
        </p>
      </AdminCard>

      <AdminCard className="mt-6">
        <h2 className="font-display text-lg text-ink">Cambiar contraseña</h2>
        <form onSubmit={onSubmit} className="mt-5 space-y-4">
          <div className="space-y-2">
            <label htmlFor="actual" className="text-sm font-medium text-ink">
              Contraseña actual
            </label>
            <PasswordInput
              id="actual"
              required
              value={contrasenaActual}
              onChange={(e) => setContrasenaActual(e.target.value)}
              className="w-full rounded-xl border border-line bg-paper px-4 py-3 text-sm text-ink focus:border-gold-deep focus:outline-none"
            />
          </div>
          <div className="space-y-2">
            <label htmlFor="nueva" className="text-sm font-medium text-ink">
              Nueva contraseña
            </label>
            <PasswordInput
              id="nueva"
              required
              value={contrasenaNueva}
              onChange={(e) => setContrasenaNueva(e.target.value)}
              className="w-full rounded-xl border border-line bg-paper px-4 py-3 text-sm text-ink focus:border-gold-deep focus:outline-none"
            />
          </div>
          <div className="space-y-2">
            <label htmlFor="confirmar" className="text-sm font-medium text-ink">
              Confirmar nueva contraseña
            </label>
            <PasswordInput
              id="confirmar"
              required
              value={confirmacion}
              onChange={(e) => setConfirmacion(e.target.value)}
              className="w-full rounded-xl border border-line bg-paper px-4 py-3 text-sm text-ink focus:border-gold-deep focus:outline-none"
            />
          </div>
          <AdminButton type="submit" disabled={enviando}>
            {enviando ? "Guardando..." : "Actualizar contraseña"}
          </AdminButton>
        </form>
      </AdminCard>
    </div>
  );
}
