"use client";

import { useEffect, useState } from "react";
import { Dialog } from "@base-ui/react/dialog";
import { toast } from "sonner";
import { X, VideoCamera, MapPin, EnvelopeSimple, WhatsappLogo, UserCircle } from "@phosphor-icons/react";
import { agendarCita, ApiError, type Responsable, type Solicitud, type TipoReunion } from "@/lib/admin-api";
import { AdminButton } from "@/components/admin/ui";
import { cn } from "@/lib/utils";

// Convierte un ISO "2026-09-10T15:30:00" (o un Date) al formato que exige
// <input type="datetime-local">, en hora LOCAL del navegador (nunca UTC: new Date(iso) ya
// interpreta un ISO sin zona como hora local, igual que el resto del panel, ver
// solicitudes/page.tsx formatearFecha).
function aInputLocal(valor: string | Date | null | undefined): string {
  if (!valor) return "";
  const fecha = typeof valor === "string" ? new Date(valor) : valor;
  if (Number.isNaN(fecha.getTime())) return "";
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${fecha.getFullYear()}-${pad(fecha.getMonth() + 1)}-${pad(fecha.getDate())}T${pad(fecha.getHours())}:${pad(fecha.getMinutes())}`;
}

// Mismo criterio que SolicitudService.esLinkDeMeetOZoom en el backend (que es quien de
// verdad decide si se acepta o no): esto solo evita que alguien confirme un link inválido
// sin darse cuenta y tenga que volver a abrir el formulario tras el error del servidor.
function esLinkDeMeetOZoom(link: string): boolean {
  try {
    const url = new URL(link);
    if (url.protocol !== "http:" && url.protocol !== "https:") return false;
    const host = url.hostname.toLowerCase();
    const hostValido = host === "meet.google.com" || host === "zoom.us" || host.endsWith(".zoom.us");
    if (!hostValido) return false;
    // El host correcto no basta: los botones de acceso rápido de abajo prellenan
    // "https://meet.google.com/" y "https://zoom.us/j/" sin código de reunión real. Mismo
    // criterio que SolicitudService.esLinkDeMeetOZoom en el backend.
    const segmentos = url.pathname.split("/");
    const ultimoSegmento = segmentos[segmentos.length - 1] ?? "";
    return ultimoSegmento.length >= 3;
  } catch {
    return false;
  }
}

export function AgendarReunionModal({
  solicitud,
  responsables,
  rolActual,
  fechaInicial,
  onClose,
  onAgendada,
}: {
  solicitud: Solicitud | null;
  responsables: Responsable[];
  rolActual: "ADMIN_GENERAL" | "ABOGADO";
  /** Prefija la fecha/hora, ej. al agendar desde un día concreto del calendario. */
  fechaInicial?: Date | null;
  onClose: () => void;
  onAgendada: (s: Solicitud) => void;
}) {
  const [fechaHora, setFechaHora] = useState("");
  const [correo, setCorreo] = useState("");
  const [telefono, setTelefono] = useState("");
  const [tipoReunion, setTipoReunion] = useState<TipoReunion>("VIRTUAL");
  const [linkReunion, setLinkReunion] = useState("");
  const [lugarReunion, setLugarReunion] = useState("");
  const [abogadoId, setAbogadoId] = useState("");
  const [enviando, setEnviando] = useState(false);

  useEffect(() => {
    if (!solicitud) return;
    // El grid del calendario (calendario-mensual.tsx) permite hacer clic en cualquier día,
    // incluidos los ya pasados -- bug real encontrado en auditoría: eso prellenaba fechaHora
    // con una fecha ya inválida sin ningún aviso, y puedeEnviar solo exigía que no estuviera
    // vacía, no que fuera futura; el error real de AgendarCitaRequest.fechaHora recién
    // aparecía como toast después de intentar enviar. Si la fecha prellenada ya quedó en el
    // pasado, se deja el campo vacío para que el admin tenga que elegir una hora futura real
    // (el atributo min de abajo ya lo guía a eso).
    const prellenado = aInputLocal(fechaInicial ?? solicitud.fechaCita);
    setFechaHora(prellenado && prellenado >= aInputLocal(new Date()) ? prellenado : "");
    setCorreo(solicitud.correo);
    setTelefono(solicitud.telefono ?? "");
    setTipoReunion(solicitud.tipoReunion ?? "VIRTUAL");
    setLinkReunion(solicitud.linkReunion ?? "");
    setLugarReunion(solicitud.lugarReunion ?? "");
    setAbogadoId(solicitud.abogadoAsignadoId ? String(solicitud.abogadoAsignadoId) : "");
  }, [solicitud, fechaInicial]);

  const esVirtual = tipoReunion === "VIRTUAL";
  const linkValido = esVirtual && esLinkDeMeetOZoom(linkReunion.trim());
  const necesitaResponsable = rolActual === "ADMIN_GENERAL";
  const puedeEnviar =
    !!fechaHora &&
    !!correo.trim() &&
    (esVirtual ? linkValido : !!lugarReunion.trim()) &&
    (!necesitaResponsable || !!abogadoId);

  async function onSubmit() {
    if (!solicitud || !puedeEnviar) return;
    setEnviando(true);
    try {
      const actualizada = await agendarCita(solicitud.id, {
        fechaHora,
        correo: correo.trim(),
        telefono: telefono.trim() || undefined,
        tipoReunion,
        linkReunion: esVirtual ? linkReunion.trim() : undefined,
        lugarReunion: esVirtual ? undefined : lugarReunion.trim(),
        abogadoId: necesitaResponsable ? Number(abogadoId) : undefined,
      });
      toast.success("Reunión agendada. Se notificó al cliente por correo y WhatsApp.");
      onAgendada(actualizada);
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo agendar la reunión.");
    } finally {
      setEnviando(false);
    }
  }

  return (
    <Dialog.Root open={!!solicitud} onOpenChange={(open) => !open && onClose()}>
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 z-50 bg-ink/40 backdrop-blur-sm" />
        <Dialog.Popup className="fixed left-1/2 top-1/2 z-50 max-h-[90vh] w-full max-w-md -translate-x-1/2 -translate-y-1/2 overflow-y-auto rounded-2xl bg-surface p-6 shadow-2xl ring-1 ring-line">
          <div className="flex items-center justify-between">
            <Dialog.Title className="font-display text-lg text-ink">
              {solicitud?.fechaCita ? "Reprogramar reunión" : "Agendar reunión"}
            </Dialog.Title>
            <Dialog.Close className="flex h-8 w-8 items-center justify-center rounded-full text-ink-soft hover:bg-ink/5">
              <X className="h-4 w-4" />
            </Dialog.Close>
          </div>
          <Dialog.Description className="mt-1 text-sm text-ink-soft">
            {solicitud?.nombre}
          </Dialog.Description>

          <div className="mt-5 space-y-4">
            <Campo label="Tipo de reunión">
              <div className="grid grid-cols-2 gap-2">
                <TipoBoton
                  activo={esVirtual}
                  onClick={() => setTipoReunion("VIRTUAL")}
                  icon={<VideoCamera className="h-4 w-4" weight={esVirtual ? "fill" : "light"} />}
                  label="Virtual"
                />
                <TipoBoton
                  activo={!esVirtual}
                  onClick={() => setTipoReunion("PRESENCIAL")}
                  icon={<MapPin className="h-4 w-4" weight={!esVirtual ? "fill" : "light"} />}
                  label="Presencial"
                />
              </div>
            </Campo>

            <Campo label="Fecha y hora">
              <input
                type="datetime-local"
                value={fechaHora}
                onChange={(e) => setFechaHora(e.target.value)}
                min={aInputLocal(new Date())}
                className="w-full rounded-xl border border-line bg-paper px-4 py-3 text-sm text-ink focus:border-gold-deep focus:outline-none"
              />
            </Campo>

            <Campo label="Correo del cliente" icon={<EnvelopeSimple className="h-4 w-4" weight="light" />}>
              <input
                type="email"
                value={correo}
                onChange={(e) => setCorreo(e.target.value)}
                placeholder="cliente@correo.com"
                className="w-full rounded-xl border border-line bg-paper px-4 py-3 text-sm text-ink focus:border-gold-deep focus:outline-none"
              />
            </Campo>

            <Campo label="WhatsApp del cliente" icon={<WhatsappLogo className="h-4 w-4" weight="light" />}>
              <input
                type="tel"
                value={telefono}
                onChange={(e) => setTelefono(e.target.value)}
                placeholder="3001234567"
                className="w-full rounded-xl border border-line bg-paper px-4 py-3 text-sm text-ink focus:border-gold-deep focus:outline-none"
              />
              <p className="mt-1.5 text-xs text-ink-soft">
                Celular colombiano (10 dígitos). Si no es válido, la confirmación solo se envía por correo.
              </p>
            </Campo>

            {esVirtual ? (
              <Campo label="Link de acceso a la reunión" icon={<VideoCamera className="h-4 w-4" weight="light" />}>
                <div className="mb-2 flex gap-1.5">
                  <button
                    type="button"
                    onClick={() => setLinkReunion("https://meet.google.com/")}
                    className="rounded-full bg-ink/5 px-3 py-1 text-xs font-medium text-ink-soft hover:bg-ink/10"
                  >
                    Google Meet
                  </button>
                  <button
                    type="button"
                    onClick={() => setLinkReunion("https://zoom.us/j/")}
                    className="rounded-full bg-ink/5 px-3 py-1 text-xs font-medium text-ink-soft hover:bg-ink/10"
                  >
                    Zoom
                  </button>
                </div>
                <input
                  type="url"
                  value={linkReunion}
                  onChange={(e) => setLinkReunion(e.target.value)}
                  placeholder="https://meet.google.com/... o https://zoom.us/j/..."
                  className="w-full rounded-xl border border-line bg-paper px-4 py-3 text-sm text-ink focus:border-gold-deep focus:outline-none"
                />
                {linkReunion.trim() && !linkValido && (
                  <p className="mt-1.5 text-xs text-red-600">Debe ser un link de Google Meet o Zoom.</p>
                )}
              </Campo>
            ) : (
              <Campo label="Lugar de la reunión" icon={<MapPin className="h-4 w-4" weight="light" />}>
                <input
                  type="text"
                  value={lugarReunion}
                  onChange={(e) => setLugarReunion(e.target.value)}
                  placeholder="Ej. Oficina SIE Jurídicos, Cra. 7 # 45-12, Bogotá"
                  className="w-full rounded-xl border border-line bg-paper px-4 py-3 text-sm text-ink focus:border-gold-deep focus:outline-none"
                />
              </Campo>
            )}

            {necesitaResponsable && (
              <Campo label="Responsable de la reunión" icon={<UserCircle className="h-4 w-4" weight="light" />}>
                <select
                  value={abogadoId}
                  onChange={(e) => setAbogadoId(e.target.value)}
                  className="w-full rounded-xl border border-line bg-paper px-4 py-3 text-sm text-ink focus:border-gold-deep focus:outline-none"
                >
                  <option value="">Selecciona un responsable...</option>
                  {responsables.map((r) => (
                    <option key={r.id} value={r.id}>
                      {r.nombre} {r.rol === "ADMIN_GENERAL" ? "(Admin)" : "(Abogado)"}
                    </option>
                  ))}
                </select>
              </Campo>
            )}
          </div>

          <AdminButton onClick={onSubmit} disabled={!puedeEnviar || enviando} className="mt-6 w-full">
            {enviando ? "Agendando..." : "Confirmar reunión"}
          </AdminButton>
          <p className="mt-3 text-center text-xs text-ink-soft">
            Al confirmar, el cliente recibe {esVirtual ? "el link" : "el lugar"} por correo y WhatsApp de inmediato.
          </p>
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  );
}

function TipoBoton({
  activo,
  onClick,
  icon,
  label,
}: {
  activo: boolean;
  onClick: () => void;
  icon: React.ReactNode;
  label: string;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "flex items-center justify-center gap-2 rounded-xl border px-4 py-2.5 text-sm font-medium transition-colors",
        activo ? "border-gold-deep bg-gold-pale/40 text-ink" : "border-line bg-paper text-ink-soft hover:bg-ink/5",
      )}
    >
      {icon}
      {label}
    </button>
  );
}

function Campo({ label, icon, children }: { label: string; icon?: React.ReactNode; children: React.ReactNode }) {
  return (
    <div className="space-y-2">
      <label className="flex items-center gap-1.5 text-sm font-medium text-ink">
        {icon}
        {label}
      </label>
      {children}
    </div>
  );
}
