"use client";

import { useEffect, useState } from "react";
import { Dialog } from "@base-ui/react/dialog";
import { toast } from "sonner";
import { Plus, X } from "@phosphor-icons/react";
import {
  listarUsuarios,
  crearAbogado,
  cambiarActivoUsuario,
  ApiError,
  type UsuarioInterno,
} from "@/lib/admin-api";
import { useAuth } from "@/lib/auth-context";
import { AdminPageHeader, AdminButton, AdminCard, Badge, EmptyState } from "@/components/admin/ui";
import { PasswordInput } from "@/components/admin/password-input";

function formatearFecha(iso: string) {
  return new Date(iso).toLocaleDateString("es-CO", { day: "numeric", month: "short", year: "numeric" });
}

export default function UsuariosPage() {
  const { sesion } = useAuth();
  const [usuarios, setUsuarios] = useState<UsuarioInterno[] | null>(null);
  const [modalAbierto, setModalAbierto] = useState(false);

  useEffect(() => {
    if (sesion?.rol === "ADMIN_GENERAL") cargar();
  }, [sesion]);

  function cargar() {
    listarUsuarios().then(setUsuarios).catch(() => toast.error("No se pudieron cargar los usuarios."));
  }

  async function onCambiarActivo(id: number, activo: boolean) {
    try {
      const actualizado = await cambiarActivoUsuario(id, activo);
      setUsuarios((prev) => prev?.map((u) => (u.id === id ? actualizado : u)) ?? null);
      toast.success(activo ? "Cuenta activada." : "Cuenta desactivada.");
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo actualizar la cuenta.");
    }
  }

  if (sesion && sesion.rol !== "ADMIN_GENERAL") {
    return (
      <EmptyState
        title="No tienes acceso a esta sección"
        description="Solo el administrador general puede gestionar cuentas de usuarios internos."
      />
    );
  }

  return (
    <div>
      <AdminPageHeader
        title="Usuarios internos"
        description="Abogados y administradores con acceso al panel."
        action={
          <AdminButton onClick={() => setModalAbierto(true)}>
            <Plus className="h-4 w-4" weight="bold" />
            Nuevo abogado
          </AdminButton>
        }
      />

      {usuarios === null ? (
        <p className="text-sm text-ink-soft">Cargando...</p>
      ) : (
        <div className="space-y-3">
          {usuarios.map((u) => (
            <AdminCard key={u.id} className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <div className="flex flex-wrap items-center gap-2">
                  <p className="font-medium text-ink">{u.nombre}</p>
                  <Badge tone={u.rol === "ADMIN_GENERAL" ? "gold" : "neutral"}>{u.rol}</Badge>
                  <Badge tone={u.activo ? "success" : "danger"}>{u.activo ? "Activo" : "Inactivo"}</Badge>
                </div>
                <p className="mt-1 text-sm text-ink-soft">
                  {u.correo} · Desde {formatearFecha(u.fechaCreacion)}
                </p>
              </div>

              {u.rol !== "ADMIN_GENERAL" && (
                <AdminButton
                  variant={u.activo ? "danger" : "secondary"}
                  onClick={() => onCambiarActivo(u.id, !u.activo)}
                >
                  {u.activo ? "Desactivar" : "Activar"}
                </AdminButton>
              )}
            </AdminCard>
          ))}
        </div>
      )}

      <ModalNuevoAbogado
        open={modalAbierto}
        onClose={() => setModalAbierto(false)}
        onCreado={(nuevo) => {
          setUsuarios((prev) => (prev ? [...prev, nuevo] : [nuevo]));
          setModalAbierto(false);
        }}
      />
    </div>
  );
}

function ModalNuevoAbogado({
  open,
  onClose,
  onCreado,
}: {
  open: boolean;
  onClose: () => void;
  onCreado: (u: UsuarioInterno) => void;
}) {
  const [nombre, setNombre] = useState("");
  const [correo, setCorreo] = useState("");
  const [contrasena, setContrasena] = useState("");
  const [enviando, setEnviando] = useState(false);

  useEffect(() => {
    if (!open) {
      setNombre("");
      setCorreo("");
      setContrasena("");
    }
  }, [open]);

  async function onSubmit() {
    if (!nombre.trim() || !correo.trim() || contrasena.length < 8) {
      toast.error("Completa nombre, correo y una contraseña de al menos 8 caracteres.");
      return;
    }
    setEnviando(true);
    try {
      const nuevo = await crearAbogado(nombre.trim(), correo.trim(), contrasena);
      toast.success("Cuenta de abogado creada.");
      onCreado(nuevo);
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo crear la cuenta.");
    } finally {
      setEnviando(false);
    }
  }

  return (
    <Dialog.Root open={open} onOpenChange={(v) => !v && onClose()}>
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 z-50 bg-ink/40 backdrop-blur-sm" />
        <Dialog.Popup className="fixed left-1/2 top-1/2 z-50 w-full max-w-sm -translate-x-1/2 -translate-y-1/2 rounded-2xl bg-surface p-6 shadow-2xl ring-1 ring-line">
          <div className="flex items-center justify-between">
            <Dialog.Title className="font-display text-lg text-ink">Nuevo abogado</Dialog.Title>
            <Dialog.Close className="flex h-8 w-8 items-center justify-center rounded-full text-ink-soft hover:bg-ink/5">
              <X className="h-4 w-4" />
            </Dialog.Close>
          </div>
          <Dialog.Description className="mt-1 text-sm text-ink-soft">
            El rol se asigna automáticamente como Abogado.
          </Dialog.Description>

          <div className="mt-5 space-y-4">
            <input
              value={nombre}
              onChange={(e) => setNombre(e.target.value)}
              placeholder="Nombre completo"
              className="w-full rounded-xl border border-line bg-paper px-4 py-3 text-sm text-ink focus:border-gold-deep focus:outline-none"
            />
            <input
              type="email"
              value={correo}
              onChange={(e) => setCorreo(e.target.value)}
              placeholder="correo@siejuridicos.com"
              className="w-full rounded-xl border border-line bg-paper px-4 py-3 text-sm text-ink focus:border-gold-deep focus:outline-none"
            />
            <PasswordInput
              value={contrasena}
              onChange={(e) => setContrasena(e.target.value)}
              placeholder="Contraseña temporal (mín. 8 caracteres)"
              className="w-full rounded-xl border border-line bg-paper px-4 py-3 text-sm text-ink focus:border-gold-deep focus:outline-none"
            />
          </div>

          <AdminButton onClick={onSubmit} disabled={enviando} className="mt-6 w-full">
            {enviando ? "Creando..." : "Crear cuenta"}
          </AdminButton>
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
