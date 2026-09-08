"use client";

import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";
import { Dialog } from "@base-ui/react/dialog";
import { toast } from "sonner";
import {
  CaretLeft,
  CaretRight,
  CalendarPlus,
  MagnifyingGlass,
  VideoCamera,
  MapPin,
  EnvelopeSimple,
  WhatsappLogo,
  X,
} from "@phosphor-icons/react";
import {
  listarCalendario,
  listarResponsables,
  listarSolicitudes,
  listarCasos,
  crearSolicitudDirecta,
  ApiError,
  type Responsable,
  type Solicitud,
  type CasoAdmin,
} from "@/lib/admin-api";
import { useAuth } from "@/lib/auth-context";
import { AdminPageHeader, AdminButton, AdminCard, AdminLoader, Badge, EmptyState } from "@/components/admin/ui";
import { AgendarReunionModal } from "@/components/admin/agendar-reunion-modal";
import { CalendarioMensual, colorResponsable, diasDeLaCuadricula } from "@/components/admin/calendario-mensual";

const MESES = [
  "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
  "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre",
];

function mismodia(a: Date, b: Date) {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
}

export default function CalendarioPage() {
  const { sesion } = useAuth();
  const esAdmin = sesion?.rol === "ADMIN_GENERAL";

  const [mes, setMes] = useState(() => new Date(new Date().getFullYear(), new Date().getMonth(), 1));
  const [diaSeleccionado, setDiaSeleccionado] = useState<Date>(() => new Date());
  const [eventos, setEventos] = useState<Solicitud[] | null>(null);
  const [responsables, setResponsables] = useState<Responsable[]>([]);
  const [filtroAbogadoId, setFiltroAbogadoId] = useState<number | undefined>(undefined);

  const [picadorAbierto, setPicadorAbierto] = useState(false);
  const [reunionEnCurso, setReunionEnCurso] = useState<{ solicitud: Solicitud; fecha: Date } | null>(null);

  const rango = useMemo(() => {
    const dias = diasDeLaCuadricula(mes);
    return { desde: dias[0], hasta: dias[dias.length - 1] };
  }, [mes]);

  const cargar = useCallback(() => {
    listarCalendario({
      desde: isoFecha(rango.desde),
      hasta: isoFecha(rango.hasta),
      abogadoId: filtroAbogadoId,
    })
      .then(setEventos)
      .catch(() => toast.error("No se pudo cargar el calendario."));
  }, [rango, filtroAbogadoId]);

  useEffect(() => {
    cargar();
  }, [cargar]);

  useEffect(() => {
    // Antes solo ADMIN_GENERAL cargaba esta lista (era el único que la usaba, para elegir
    // responsable). Ahora un ABOGADO también la necesita para poder sumar colegas como
    // corresponsables de su propia reunión (ver AgendarReunionModal).
    listarResponsables().then(setResponsables).catch(() => {});
  }, []);

  function irAMesAnterior() {
    setMes((m) => new Date(m.getFullYear(), m.getMonth() - 1, 1));
  }
  function irAMesSiguiente() {
    setMes((m) => new Date(m.getFullYear(), m.getMonth() + 1, 1));
  }
  function irAHoy() {
    const hoy = new Date();
    setMes(new Date(hoy.getFullYear(), hoy.getMonth(), 1));
    setDiaSeleccionado(hoy);
  }

  const eventosDelDiaSeleccionado = (eventos ?? [])
    .filter((e) => e.fechaCita && mismodia(new Date(e.fechaCita), diaSeleccionado))
    .sort((a, b) => new Date(a.fechaCita!).getTime() - new Date(b.fechaCita!).getTime());

  function onAgendada(actualizada: Solicitud) {
    setEventos((prev) => {
      if (!prev) return prev;
      const existe = prev.some((s) => s.id === actualizada.id);
      return existe ? prev.map((s) => (s.id === actualizada.id ? actualizada : s)) : [...prev, actualizada];
    });
    setReunionEnCurso(null);
  }

  return (
    <div>
      <AdminPageHeader
        title="Calendario"
        description={
          esAdmin
            ? "Reuniones de todo el equipo. Filtra por abogado si necesitas ver solo las suyas."
            : "Tus reuniones agendadas."
        }
        action={
          <AdminButton onClick={() => setPicadorAbierto(true)}>
            <CalendarPlus className="h-4 w-4" weight="light" />
            Agendar reunión
          </AdminButton>
        }
      />

      <div className="mb-5 flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={irAMesAnterior}
            aria-label="Mes anterior"
            className="flex h-9 w-9 items-center justify-center rounded-full text-ink-soft ring-1 ring-line hover:bg-ink/5"
          >
            <CaretLeft className="h-4 w-4" />
          </button>
          <p className="min-w-[160px] text-center font-display text-lg text-ink">
            {MESES[mes.getMonth()]} {mes.getFullYear()}
          </p>
          <button
            type="button"
            onClick={irAMesSiguiente}
            aria-label="Mes siguiente"
            className="flex h-9 w-9 items-center justify-center rounded-full text-ink-soft ring-1 ring-line hover:bg-ink/5"
          >
            <CaretRight className="h-4 w-4" />
          </button>
          <AdminButton variant="ghost" onClick={irAHoy} className="ml-1 text-xs">
            Hoy
          </AdminButton>
        </div>

        {esAdmin && (
          <select
            value={filtroAbogadoId ?? ""}
            onChange={(e) => setFiltroAbogadoId(e.target.value ? Number(e.target.value) : undefined)}
            className="rounded-full border border-line bg-surface px-4 py-2 text-sm text-ink focus:border-gold-deep focus:outline-none"
          >
            <option value="">Todo el equipo</option>
            {responsables.map((r) => (
              <option key={r.id} value={r.id}>
                {r.nombre}
              </option>
            ))}
          </select>
        )}
      </div>

      {eventos === null ? (
        <AdminLoader />
      ) : (
        <div className="grid gap-5 lg:grid-cols-[1fr_320px]">
          <CalendarioMensual
            mes={mes}
            eventos={eventos}
            diaSeleccionado={diaSeleccionado}
            onSeleccionarDia={setDiaSeleccionado}
            mostrarResponsable={esAdmin}
          />

          <div>
            <p className="mb-3 text-sm font-medium text-ink">
              {diaSeleccionado.toLocaleDateString("es-CO", { weekday: "long", day: "numeric", month: "long" })}
            </p>
            {eventosDelDiaSeleccionado.length === 0 ? (
              <EmptyState title="Sin reuniones" description="No hay reuniones agendadas este día." />
            ) : (
              <div className="space-y-3">
                {eventosDelDiaSeleccionado.map((evento) => {
                  const color = esAdmin ? colorResponsable(evento.responsables[0]?.nombre ?? null) : colorResponsable(null);
                  return (
                    <AdminCard key={evento.id} className="!p-4">
                      <div className="flex items-start justify-between gap-2">
                        <div className="min-w-0">
                          <div className="flex flex-wrap items-center gap-1.5">
                            <p className="text-sm font-medium text-ink">
                              {new Date(evento.fechaCita!).toLocaleTimeString("es-CO", { hour: "numeric", minute: "2-digit" })}
                              {" · "}
                              {evento.nombre}
                            </p>
                            <span className="inline-flex items-center gap-1 rounded-full bg-ink/[0.04] px-2 py-0.5 text-[10.5px] font-medium text-ink-soft">
                              {evento.tipoReunion === "PRESENCIAL" ? (
                                <MapPin className="h-3 w-3" weight="light" />
                              ) : (
                                <VideoCamera className="h-3 w-3" weight="light" />
                              )}
                              {evento.tipoReunion === "PRESENCIAL" ? "Presencial" : "Virtual"}
                            </span>
                          </div>
                          <p className="mt-1 flex items-center gap-1.5 truncate text-xs text-ink-soft">
                            <EnvelopeSimple className="h-3.5 w-3.5 shrink-0" weight="light" />
                            {evento.correo}
                          </p>
                          {evento.telefono && (
                            <p className="mt-0.5 flex items-center gap-1.5 truncate text-xs text-ink-soft">
                              <WhatsappLogo className="h-3.5 w-3.5 shrink-0" weight="light" />
                              {evento.telefono}
                            </p>
                          )}
                          {evento.tipoReunion === "PRESENCIAL" && evento.lugarReunion && (
                            <p className="mt-0.5 flex items-start gap-1.5 text-xs text-ink-soft">
                              <MapPin className="mt-0.5 h-3.5 w-3.5 shrink-0" weight="light" />
                              <span>{evento.lugarReunion}</span>
                            </p>
                          )}
                          {esAdmin && evento.responsables.length > 0 && (
                            <span className={`mt-2 inline-flex rounded-full px-2 py-0.5 text-[11px] font-medium ${color.bg} ${color.text}`}>
                              {evento.responsables.map((r) => r.nombre).join(", ")}
                            </span>
                          )}
                        </div>
                      </div>
                      <div className="mt-3 flex items-center gap-2">
                        {evento.tipoReunion === "VIRTUAL" && evento.linkReunion && (
                          <a
                            href={evento.linkReunion}
                            target="_blank"
                            rel="noreferrer"
                            className="inline-flex items-center gap-1.5 text-xs font-medium text-gold-deep hover:underline"
                          >
                            <VideoCamera className="h-3.5 w-3.5" weight="light" />
                            Abrir link
                          </a>
                        )}
                        <button
                          type="button"
                          onClick={() => setReunionEnCurso({ solicitud: evento, fecha: new Date(evento.fechaCita!) })}
                          className="text-xs font-medium text-ink-soft hover:text-ink hover:underline"
                        >
                          Reprogramar
                        </button>
                      </div>
                    </AdminCard>
                  );
                })}
              </div>
            )}
            <AdminButton
              variant="secondary"
              className="mt-3 w-full"
              onClick={() => {
                setPicadorAbierto(true);
              }}
            >
              <CalendarPlus className="h-4 w-4" weight="light" />
              Agendar este día
            </AdminButton>
          </div>
        </div>
      )}

      <OrigenReunionPicker
        abierto={picadorAbierto}
        onClose={() => setPicadorAbierto(false)}
        onElegir={(s) => {
          setPicadorAbierto(false);
          setReunionEnCurso({ solicitud: s, fecha: diaSeleccionado });
        }}
      />

      <AgendarReunionModal
        solicitud={reunionEnCurso?.solicitud ?? null}
        fechaInicial={reunionEnCurso?.fecha ?? null}
        responsables={responsables}
        rolActual={esAdmin ? "ADMIN_GENERAL" : "ABOGADO"}
        onClose={() => setReunionEnCurso(null)}
        onAgendada={onAgendada}
      />
    </div>
  );
}

function isoFecha(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

// Selector de para quién es la reunión -- pedido explícito del usuario: antes SOLO se podía
// agendar sobre una solicitud ya recibida por el formulario/chatbot/WhatsApp. Ahora, además
// de eso, se puede agendar para uno de los casos ya existentes en el sistema (Judiciales,
// Superintendencia, Procesos Comisaría, Manuales) o para un cliente que todavía no está en
// el sistema en absoluto. Las pestañas "Caso" y "Cliente nuevo" no tienen una Solicitud a la
// que engancharse todavía, así que primero crean una directamente (ver
// SolicitudService.crearDirecta, nunca dispara los correos de "recibimos tu solicitud") y
// luego siguen el mismo camino de siempre: AgendarReunionModal sobre esa Solicitud nueva.
type PestanaOrigen = "SOLICITUD" | "CASO" | "NUEVO";

function OrigenReunionPicker({
  abierto,
  onClose,
  onElegir,
}: {
  abierto: boolean;
  onClose: () => void;
  onElegir: (s: Solicitud) => void;
}) {
  const [pestana, setPestana] = useState<PestanaOrigen>("SOLICITUD");
  const [busqueda, setBusqueda] = useState("");
  const [solicitudes, setSolicitudes] = useState<Solicitud[] | null>(null);
  const [casos, setCasos] = useState<CasoAdmin[] | null>(null);
  const [creando, setCreando] = useState(false);
  const [nuevoNombre, setNuevoNombre] = useState("");
  const [nuevoCorreo, setNuevoCorreo] = useState("");
  const [nuevoTelefono, setNuevoTelefono] = useState("");

  useEffect(() => {
    if (!abierto) return;
    setPestana("SOLICITUD");
    setBusqueda("");
    setNuevoNombre("");
    setNuevoCorreo("");
    setNuevoTelefono("");
    listarSolicitudes()
      .then(setSolicitudes)
      .catch(() => toast.error("No se pudieron cargar las solicitudes."));
    listarCasos()
      .then(setCasos)
      .catch(() => toast.error("No se pudieron cargar los casos."));
  }, [abierto]);

  const solicitudesFiltradas = (solicitudes ?? [])
    .filter((s) => s.estado !== "CERRADO")
    .filter((s) => {
      const texto = busqueda.trim().toLowerCase();
      if (!texto) return true;
      return s.nombre.toLowerCase().includes(texto) || s.correo.toLowerCase().includes(texto);
    })
    .sort((a, b) => (a.fechaCita ? 1 : 0) - (b.fechaCita ? 1 : 0));

  const casosFiltrados = (casos ?? []).filter((c) => {
    const texto = busqueda.trim().toLowerCase();
    if (!texto) return true;
    return (
      c.nombreCliente.toLowerCase().includes(texto) ||
      (c.correoCliente ?? "").toLowerCase().includes(texto) ||
      (c.radicadoId ?? "").toLowerCase().includes(texto)
    );
  });

  async function elegirCaso(c: CasoAdmin) {
    if (!c.correoCliente) {
      toast.error("Este caso todavía no tiene un correo capturado -- no se le puede agendar una reunión sin un dato de contacto.");
      return;
    }
    setCreando(true);
    try {
      const solicitud = await crearSolicitudDirecta({
        nombre: c.nombreCliente,
        correo: c.correoCliente,
        telefono: c.telefonoCliente ?? undefined,
        mensaje: `Reunión agendada para el caso ${c.fuenteVisible}${c.radicadoId ? ` (radicado ${c.radicadoId})` : ""}.`,
      });
      onElegir(solicitud);
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo iniciar la reunión para este caso.");
    } finally {
      setCreando(false);
    }
  }

  async function crearClienteNuevo(e: FormEvent) {
    e.preventDefault();
    if (!nuevoNombre.trim() || !nuevoCorreo.trim()) return;
    setCreando(true);
    try {
      const solicitud = await crearSolicitudDirecta({
        nombre: nuevoNombre.trim(),
        correo: nuevoCorreo.trim(),
        telefono: nuevoTelefono.trim() || undefined,
      });
      onElegir(solicitud);
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo iniciar la reunión para este cliente.");
    } finally {
      setCreando(false);
    }
  }

  return (
    <Dialog.Root open={abierto} onOpenChange={(open) => !open && onClose()}>
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 z-50 bg-ink/40 backdrop-blur-sm" />
        <Dialog.Popup className="fixed left-1/2 top-1/2 z-50 max-h-[85vh] w-full max-w-md -translate-x-1/2 -translate-y-1/2 overflow-hidden rounded-2xl bg-surface shadow-2xl ring-1 ring-line">
          <div className="flex items-center justify-between p-6 pb-3">
            <Dialog.Title className="font-display text-lg text-ink">¿Para quién es la reunión?</Dialog.Title>
            <Dialog.Close className="flex h-8 w-8 items-center justify-center rounded-full text-ink-soft hover:bg-ink/5">
              <X className="h-4 w-4" />
            </Dialog.Close>
          </div>

          <div className="flex gap-1 px-6">
            {([
              ["SOLICITUD", "Solicitud"],
              ["CASO", "Caso existente"],
              ["NUEVO", "Cliente nuevo"],
            ] as [PestanaOrigen, string][]).map(([valor, etiqueta]) => (
              <button
                key={valor}
                type="button"
                onClick={() => {
                  setPestana(valor);
                  setBusqueda("");
                }}
                className={`rounded-full px-3 py-1.5 text-xs font-medium transition-colors ${
                  pestana === valor ? "bg-gold text-ink-fixed" : "text-ink-soft hover:bg-ink/5"
                }`}
              >
                {etiqueta}
              </button>
            ))}
          </div>

          {pestana !== "NUEVO" && (
            <div className="mt-3 px-6">
              <div className="relative">
                <MagnifyingGlass className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-soft" weight="light" />
                <input
                  value={busqueda}
                  onChange={(e) => setBusqueda(e.target.value)}
                  placeholder={pestana === "SOLICITUD" ? "Buscar por nombre o correo..." : "Buscar por nombre, correo o radicado..."}
                  className="w-full rounded-xl border border-line bg-paper py-2.5 pl-10 pr-4 text-sm text-ink focus:border-gold-deep focus:outline-none"
                  autoFocus
                />
              </div>
            </div>
          )}

          {pestana === "SOLICITUD" && (
            <div className="mt-3 max-h-[50vh] overflow-y-auto px-3 pb-6">
              {solicitudes === null ? (
                <AdminLoader size="sm" className="py-8" />
              ) : solicitudesFiltradas.length === 0 ? (
                <p className="px-3 py-8 text-center text-sm text-ink-soft">Sin resultados.</p>
              ) : (
                solicitudesFiltradas.map((s) => (
                  <button
                    key={s.id}
                    type="button"
                    onClick={() => onElegir(s)}
                    className="flex w-full items-center justify-between gap-3 rounded-xl px-3 py-2.5 text-left hover:bg-ink/5"
                  >
                    <div className="min-w-0">
                      <p className="truncate text-sm font-medium text-ink">{s.nombre}</p>
                      <p className="truncate text-xs text-ink-soft">{s.correo}</p>
                    </div>
                    {s.fechaCita ? <Badge tone="warning">Ya tiene cita</Badge> : <Badge tone="gold">Sin cita</Badge>}
                  </button>
                ))
              )}
            </div>
          )}

          {pestana === "CASO" && (
            <div className="mt-3 max-h-[50vh] overflow-y-auto px-3 pb-6">
              {casos === null ? (
                <AdminLoader size="sm" className="py-8" />
              ) : casosFiltrados.length === 0 ? (
                <p className="px-3 py-8 text-center text-sm text-ink-soft">Sin resultados.</p>
              ) : (
                casosFiltrados.map((c) => (
                  <button
                    key={c.id}
                    type="button"
                    disabled={creando}
                    onClick={() => elegirCaso(c)}
                    className="flex w-full items-center justify-between gap-3 rounded-xl px-3 py-2.5 text-left hover:bg-ink/5 disabled:opacity-50"
                  >
                    <div className="min-w-0">
                      <p className="truncate text-sm font-medium text-ink">{c.nombreCliente}</p>
                      <p className="truncate text-xs text-ink-soft">{c.correoCliente ?? "Sin correo capturado"}</p>
                    </div>
                    <Badge tone="gold">{c.fuenteVisible}</Badge>
                  </button>
                ))
              )}
            </div>
          )}

          {pestana === "NUEVO" && (
            <form onSubmit={crearClienteNuevo} className="mt-3 space-y-3 px-6 pb-6">
              <p className="text-xs text-ink-soft">
                Para un cliente que todavía no está en el sistema (ni como solicitud ni como caso).
              </p>
              <input
                value={nuevoNombre}
                onChange={(e) => setNuevoNombre(e.target.value)}
                placeholder="Nombre completo"
                required
                className="w-full rounded-xl border border-line bg-paper px-4 py-2.5 text-sm text-ink focus:border-gold-deep focus:outline-none"
              />
              <input
                type="email"
                value={nuevoCorreo}
                onChange={(e) => setNuevoCorreo(e.target.value)}
                placeholder="Correo"
                required
                className="w-full rounded-xl border border-line bg-paper px-4 py-2.5 text-sm text-ink focus:border-gold-deep focus:outline-none"
              />
              <input
                type="tel"
                value={nuevoTelefono}
                onChange={(e) => setNuevoTelefono(e.target.value)}
                placeholder="WhatsApp (opcional)"
                className="w-full rounded-xl border border-line bg-paper px-4 py-2.5 text-sm text-ink focus:border-gold-deep focus:outline-none"
              />
              <AdminButton type="submit" disabled={creando || !nuevoNombre.trim() || !nuevoCorreo.trim()} className="w-full">
                {creando ? "Creando..." : "Continuar"}
              </AdminButton>
            </form>
          )}
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
