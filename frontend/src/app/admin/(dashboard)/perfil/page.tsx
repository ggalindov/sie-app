"use client";

import { useEffect, useState, type FormEvent } from "react";
import { toast } from "sonner";
import { CheckCircle, UploadSimple } from "@phosphor-icons/react";
import { cambiarContrasena, consultarMiFirma, subirMiFirma, ApiError } from "@/lib/admin-api";
import { useAuth } from "@/lib/auth-context";
import { AdminPageHeader, AdminCard, AdminButton } from "@/components/admin/ui";
import { PasswordInput } from "@/components/admin/password-input";
import { SignatureCanvas } from "@/components/firma/signature-canvas";

export default function PerfilPage() {
  const { sesion } = useAuth();
  const [contrasenaActual, setContrasenaActual] = useState("");
  const [contrasenaNueva, setContrasenaNueva] = useState("");
  const [confirmacion, setConfirmacion] = useState("");
  const [enviando, setEnviando] = useState(false);

  const [tieneFirma, setTieneFirma] = useState<boolean | null>(null);
  const [firmaPng, setFirmaPng] = useState<Blob | null>(null);
  const [subiendoFirma, setSubiendoFirma] = useState(false);

  useEffect(() => {
    consultarMiFirma()
      .then((r) => setTieneFirma(r.tieneFirma))
      .catch(() => setTieneFirma(false));
  }, []);

  async function onSubirFirmaDibujada() {
    if (!firmaPng) {
      toast.error("Dibuja tu firma antes de guardar.");
      return;
    }
    setSubiendoFirma(true);
    try {
      await subirMiFirma(new File([firmaPng], "firma.png", { type: "image/png" }));
      toast.success("Firma guardada. El motor de firma la usará automáticamente en tus documentos pendientes.");
      setTieneFirma(true);
      setFirmaPng(null);
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo guardar la firma.");
    } finally {
      setSubiendoFirma(false);
    }
  }

  async function onSubirFirmaArchivo(archivo: File) {
    setSubiendoFirma(true);
    try {
      await subirMiFirma(archivo);
      toast.success("Firma guardada. El motor de firma la usará automáticamente en tus documentos pendientes.");
      setTieneFirma(true);
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo guardar la firma.");
    } finally {
      setSubiendoFirma(false);
    }
  }

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
        <div className="flex items-center justify-between">
          <h2 className="font-display text-lg text-ink">Mi firma</h2>
          {tieneFirma && (
            <span className="inline-flex items-center gap-1.5 text-xs font-medium text-emerald-700">
              <CheckCircle className="h-4 w-4" weight="fill" /> Guardada
            </span>
          )}
        </div>
        <p className="mt-1 text-xs text-ink-soft">
          El motor de firma electrónica usa esta imagen para insertar tu firma automáticamente en los documentos donde
          quedes como firmante de staff. Dibújala abajo o sube una imagen (PNG/JPEG, máx. 2MB).
        </p>

        {tieneFirma === null ? null : (
          <div className="mt-4 space-y-3">
            <SignatureCanvas onCambio={(png) => setFirmaPng(png)} />
            <div className="flex flex-wrap items-center gap-3">
              <AdminButton onClick={onSubirFirmaDibujada} disabled={subiendoFirma || !firmaPng}>
                {subiendoFirma ? "Guardando..." : "Guardar firma dibujada"}
              </AdminButton>
              <label className="inline-flex cursor-pointer items-center gap-1.5 text-xs font-medium text-ink-soft hover:text-ink">
                <UploadSimple className="h-3.5 w-3.5" weight="light" /> O sube una imagen
                <input
                  type="file"
                  accept="image/png,image/jpeg"
                  className="hidden"
                  onChange={(e) => {
                    const archivo = e.target.files?.[0];
                    if (archivo) onSubirFirmaArchivo(archivo);
                  }}
                />
              </label>
            </div>
          </div>
        )}
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
