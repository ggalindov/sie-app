"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useRouter } from "next/navigation";
import { toast } from "sonner";
import { MagnifyingGlass, Plus, Trash, UploadSimple, X } from "@phosphor-icons/react";
import {
  crearDocumentoFirma,
  listarUsuarios,
  listarClientesCrm,
  crearClienteCrm,
  listarCasos,
  ApiError,
  type UsuarioInterno,
  type ClienteCrm,
  type CasoAdmin,
  type CanalNotificacionFirma,
  type ParteAFirmarInput,
} from "@/lib/admin-api";
import { AdminPageHeader, AdminButton, AdminCard, EmptyState } from "@/components/admin/ui";
import { useAuth } from "@/lib/auth-context";

type ParteSeleccionada = { cliente: ClienteCrm; canal: CanalNotificacionFirma };

export default function NuevoDocumentoPage() {
  const router = useRouter();
  const { sesion } = useAuth();

  const [archivo, setArchivo] = useState<File | null>(null);
  const [nombre, setNombre] = useState("");
  const [casoId, setCasoId] = useState<string>("");
  const [casos, setCasos] = useState<CasoAdmin[]>([]);

  const [abogados, setAbogados] = useState<UsuarioInterno[]>([]);
  const [abogadosElegidos, setAbogadosElegidos] = useState<number[]>([]);

  const [busqueda, setBusqueda] = useState("");
  const [resultadosCrm, setResultadosCrm] = useState<ClienteCrm[]>([]);
  const [partes, setPartes] = useState<ParteSeleccionada[]>([]);
  const [mostrarNuevoContacto, setMostrarNuevoContacto] = useState(false);

  const [enviando, setEnviando] = useState(false);

  useEffect(() => {
    listarUsuarios()
      .then((todos) => setAbogados(todos.filter((u) => u.rol === "ABOGADO" && u.activo)))
      .catch(() => toast.error("No se pudieron cargar los abogados."));
    listarCasos()
      .then(setCasos)
      .catch(() => {
        /* el caso es opcional; si falla la carga simplemente no se ofrece el selector */
      });
  }, []);

  useEffect(() => {
    if (busqueda.trim().length < 2) {
      setResultadosCrm([]);
      return;
    }
    const id = setTimeout(() => {
      listarClientesCrm({ busqueda: busqueda.trim(), tamanoPagina: 8 })
        .then((pagina) => setResultadosCrm(pagina.contenido))
        .catch(() => undefined);
    }, 300);
    return () => clearTimeout(id);
  }, [busqueda]);

  const idsYaElegidos = useMemo(() => new Set(partes.map((p) => p.cliente.id)), [partes]);

  function agregarParte(cliente: ClienteCrm) {
    if (idsYaElegidos.has(cliente.id)) return;
    setPartes((prev) => [...prev, { cliente, canal: "EMAIL" }]);
    setBusqueda("");
    setResultadosCrm([]);
  }

  function quitarParte(clienteId: number) {
    setPartes((prev) => prev.filter((p) => p.cliente.id !== clienteId));
  }

  function cambiarCanal(clienteId: number, canal: CanalNotificacionFirma) {
    setPartes((prev) => prev.map((p) => (p.cliente.id === clienteId ? { ...p, canal } : p)));
  }

  function alternarAbogado(id: number) {
    setAbogadosElegidos((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]));
  }

  async function onSubmit() {
    if (!archivo) {
      toast.error("Adjunta el PDF del documento.");
      return;
    }
    if (!nombre.trim()) {
      toast.error("Ponle un nombre al documento.");
      return;
    }
    if (partes.length === 0) {
      toast.error("Asigna al menos una parte que deba firmar.");
      return;
    }
    for (const p of partes) {
      if (p.canal === "WHATSAPP" && !p.cliente.telefono) {
        toast.error(`${p.cliente.nombre} no tiene teléfono registrado para notificar por WhatsApp.`);
        return;
      }
      if (!p.cliente.correo) {
        toast.error(`${p.cliente.nombre} necesita un correo registrado (el documento final siempre se entrega por correo).`);
        return;
      }
    }

    const cuerpo: { clienteCrmId: number; canal: CanalNotificacionFirma }[] = partes.map((p) => ({
      clienteCrmId: p.cliente.id,
      canal: p.canal,
    }));

    setEnviando(true);
    try {
      const creado = await crearDocumentoFirma({
        archivo,
        nombre: nombre.trim(),
        casoId: casoId ? Number(casoId) : undefined,
        abogadoIds: abogadosElegidos,
        partes: cuerpo as ParteAFirmarInput[],
      });
      toast.success("Documento emitido. El motor de firma ya está en marcha.");
      router.push(`/admin/documentos/${creado.id}`);
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo emitir el documento.");
    } finally {
      setEnviando(false);
    }
  }

  // Bug real: esta página no tenía ningún guard de rol -- a diferencia del resto del panel,
  // un ABOGADO podía llenar todo el formulario (el backend solo rechaza con 403 hasta el
  // submit final, ver DocumentoAdminController.crear() -- @PreAuthorize ADMIN_GENERAL)
  // y recién ahí enterarse con un toast genérico de que no tenía permiso.
  if (sesion && sesion.rol !== "ADMIN_GENERAL") {
    return (
      <EmptyState
        title="No tienes acceso a esta sección"
        description="Solo el administrador general puede emitir documentos nuevos para firma."
      />
    );
  }

  return (
    <div>
      <AdminPageHeader
        title="Nuevo documento para firma"
        description="Sube el PDF, asigna quién debe firmarlo y el motor se encarga del resto: firmas de staff, notificación a las partes, sellado y entrega final."
      />

      <div className="space-y-6">
        <AdminCard accent>
          <h2 className="font-display text-lg text-ink">1. Documento</h2>
          <div className="mt-4 grid gap-4 sm:grid-cols-2">
            <div>
              <label className="mb-1.5 block text-xs font-medium text-ink-soft">Nombre del documento</label>
              <input
                value={nombre}
                onChange={(e) => setNombre(e.target.value)}
                placeholder="Contrato de prestación de servicios — Cliente X"
                className="w-full rounded-lg border border-line bg-paper px-3.5 py-2.5 text-sm text-ink outline-none focus:border-gold"
              />
            </div>
            <div>
              <label className="mb-1.5 block text-xs font-medium text-ink-soft">Caso relacionado (opcional)</label>
              <BuscadorCaso casos={casos} casoId={casoId} onChange={setCasoId} />
            </div>
          </div>
          <div className="mt-4">
            <label className="mb-1.5 block text-xs font-medium text-ink-soft">Archivo PDF</label>
            <label className="flex cursor-pointer items-center justify-center gap-2 rounded-xl border border-dashed border-line bg-paper px-4 py-6 text-sm text-ink-soft hover:border-gold">
              <UploadSimple className="h-4 w-4" weight="light" />
              {archivo ? archivo.name : "Haz clic para elegir un PDF (máx. 20MB)"}
              <input
                type="file"
                accept="application/pdf"
                className="hidden"
                onChange={(e) => setArchivo(e.target.files?.[0] ?? null)}
              />
            </label>
          </div>
        </AdminCard>

        <AdminCard accent>
          <h2 className="font-display text-lg text-ink">2. Abogados que deben firmar</h2>
          <p className="mt-1 text-xs text-ink-soft">
            Tú (gerencia) quedas siempre como firmante. Elige los abogados que también deban firmar este documento.
          </p>
          {abogados.length === 0 ? (
            <p className="mt-3 text-sm text-ink-soft">No hay abogados activos registrados.</p>
          ) : (
            <div className="mt-3 flex flex-wrap gap-2">
              {abogados.map((a) => (
                <button
                  key={a.id}
                  type="button"
                  onClick={() => alternarAbogado(a.id)}
                  className={`rounded-full px-3.5 py-1.5 text-sm font-medium transition-colors ${
                    abogadosElegidos.includes(a.id) ? "bg-gold text-ink-fixed" : "bg-ink/5 text-ink-soft hover:bg-ink/10"
                  }`}
                >
                  {a.nombre}
                </button>
              ))}
            </div>
          )}
        </AdminCard>

        <AdminCard accent>
          <h2 className="font-display text-lg text-ink">3. Partes que deben firmar</h2>
          <p className="mt-1 text-xs text-ink-soft">
            Busca en la biblioteca de contactos o crea uno nuevo. Una empresa solo puede notificarse por correo; una
            persona natural puede elegir correo o WhatsApp.
          </p>

          <div className="relative mt-4">
            <MagnifyingGlass className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-soft" />
            <input
              value={busqueda}
              onChange={(e) => setBusqueda(e.target.value)}
              placeholder="Buscar contacto por nombre, correo o identificación..."
              className="w-full rounded-lg border border-line bg-paper px-10 py-2.5 text-sm text-ink outline-none focus:border-gold"
            />
            {resultadosCrm.length > 0 && (
              // Mismo refuerzo que BuscadorCaso más abajo (ver ese comentario): capa
              // compuesta propia para evitar que Chrome pinte mal este panel sobre
              // cualquier tarjeta que llegue a quedar justo debajo.
              <div className="isolate absolute z-10 mt-1 w-full transform-gpu overflow-hidden rounded-lg border border-line bg-surface shadow-lg will-change-transform">
                {resultadosCrm.map((c) => (
                  <button
                    key={c.id}
                    type="button"
                    onClick={() => agregarParte(c)}
                    disabled={idsYaElegidos.has(c.id)}
                    className="flex w-full items-center justify-between px-4 py-2.5 text-left text-sm hover:bg-ink/5 disabled:opacity-40"
                  >
                    <span>
                      {c.nombre} <span className="text-xs text-ink-soft">({c.tipo === "EMPRESA" ? "Empresa" : "Persona natural"})</span>
                    </span>
                    <span className="text-xs text-ink-soft">{c.correo ?? c.telefono ?? ""}</span>
                  </button>
                ))}
              </div>
            )}
          </div>

          <button
            type="button"
            onClick={() => setMostrarNuevoContacto((v) => !v)}
            className="mt-3 inline-flex items-center gap-1.5 text-xs font-medium text-gold-deep hover:underline"
          >
            <Plus className="h-3.5 w-3.5" weight="bold" /> Crear un contacto nuevo
          </button>

          {mostrarNuevoContacto && (
            <NuevoContactoInline
              onCreado={(cliente) => {
                agregarParte(cliente);
                setMostrarNuevoContacto(false);
              }}
            />
          )}

          {partes.length > 0 && (
            <div className="mt-4 space-y-2">
              {partes.map((p) => (
                <div key={p.cliente.id} className="flex items-center justify-between gap-3 rounded-lg bg-ink/[0.03] px-4 py-3">
                  <div className="min-w-0">
                    <p className="text-sm font-medium text-ink">{p.cliente.nombre}</p>
                    <p className="text-xs text-ink-soft">{p.cliente.tipo === "EMPRESA" ? "Empresa" : "Persona natural"}</p>
                  </div>
                  <div className="flex shrink-0 items-center gap-2">
                    {p.cliente.tipo === "EMPRESA" ? (
                      <span className="rounded-full bg-ink/8 px-3 py-1 text-xs text-ink-soft">Correo (único canal para empresas)</span>
                    ) : (
                      <select
                        value={p.canal}
                        onChange={(e) => cambiarCanal(p.cliente.id, e.target.value as CanalNotificacionFirma)}
                        className="rounded-full border border-line bg-paper px-3 py-1 text-xs text-ink outline-none focus:border-gold"
                      >
                        <option value="EMAIL">Correo</option>
                        <option value="WHATSAPP">WhatsApp</option>
                      </select>
                    )}
                    <button
                      type="button"
                      onClick={() => quitarParte(p.cliente.id)}
                      className="flex h-7 w-7 items-center justify-center rounded-full text-ink-soft hover:bg-red-50 hover:text-red-700"
                      aria-label="Quitar"
                    >
                      <Trash className="h-3.5 w-3.5" weight="light" />
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </AdminCard>

        <div className="flex justify-end">
          <AdminButton onClick={onSubmit} disabled={enviando}>
            {enviando ? "Emitiendo..." : "Emitir documento"}
          </AdminButton>
        </div>
      </div>
    </div>
  );
}

function NuevoContactoInline({ onCreado }: { onCreado: (cliente: ClienteCrm) => void }) {
  const [tipo, setTipo] = useState<"PERSONA_NATURAL" | "EMPRESA">("PERSONA_NATURAL");
  const [nombre, setNombre] = useState("");
  const [correo, setCorreo] = useState("");
  const [telefono, setTelefono] = useState("");
  const [cedulaNit, setCedulaNit] = useState("");
  const [creando, setCreando] = useState(false);

  async function crear() {
    if (!nombre.trim()) {
      toast.error("El contacto necesita un nombre.");
      return;
    }
    if (!correo.trim()) {
      toast.error("El contacto necesita un correo (el documento final siempre se entrega por correo).");
      return;
    }
    setCreando(true);
    try {
      const cliente = await crearClienteCrm({
        tipo,
        nombre: nombre.trim(),
        correo: correo.trim(),
        telefono: telefono.trim() || undefined,
        cedulaNit: cedulaNit.trim() || undefined,
      });
      toast.success("Contacto creado.");
      onCreado(cliente);
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo crear el contacto.");
    } finally {
      setCreando(false);
    }
  }

  return (
    <div className="mt-3 rounded-xl border border-line bg-paper p-4">
      <div className="flex items-center justify-between">
        <div className="flex gap-2">
          {(["PERSONA_NATURAL", "EMPRESA"] as const).map((t) => (
            <button
              key={t}
              type="button"
              onClick={() => setTipo(t)}
              className={`rounded-full px-3 py-1 text-xs font-medium ${
                tipo === t ? "bg-ink text-paper" : "bg-ink/5 text-ink-soft"
              }`}
            >
              {t === "EMPRESA" ? "Empresa" : "Persona natural"}
            </button>
          ))}
        </div>
      </div>
      <div className="mt-3 grid gap-3 sm:grid-cols-2">
        <input
          value={nombre}
          onChange={(e) => setNombre(e.target.value)}
          placeholder={tipo === "EMPRESA" ? "Razón social" : "Nombre completo"}
          className="rounded-lg border border-line bg-surface px-3 py-2 text-sm outline-none focus:border-gold"
        />
        <input
          value={cedulaNit}
          onChange={(e) => setCedulaNit(e.target.value)}
          placeholder={tipo === "EMPRESA" ? "NIT" : "Cédula"}
          className="rounded-lg border border-line bg-surface px-3 py-2 text-sm outline-none focus:border-gold"
        />
        <input
          value={correo}
          onChange={(e) => setCorreo(e.target.value)}
          placeholder="Correo"
          className="rounded-lg border border-line bg-surface px-3 py-2 text-sm outline-none focus:border-gold"
        />
        <input
          value={telefono}
          onChange={(e) => setTelefono(e.target.value)}
          placeholder="Teléfono (opcional para empresa)"
          disabled={tipo === "EMPRESA"}
          className="rounded-lg border border-line bg-surface px-3 py-2 text-sm outline-none focus:border-gold disabled:opacity-50"
        />
      </div>
      <div className="mt-3 flex justify-end">
        <AdminButton type="button" onClick={crear} disabled={creando}>
          {creando ? "Creando..." : "Crear y asignar"}
        </AdminButton>
      </div>
    </div>
  );
}

// Reemplaza el <select> nativo que antes listaba TODOS los casos de la firma de un tirón
// (pedido explícito del usuario: un desplegable con cientos de radicados era imposible de
// usar a simple vista). Mismo patrón de buscador que ya usa el paso 3 (partes a firmar):
// un input con lupa + lista filtrada debajo, salvo que aquí el filtro es 100% local (los
// casos ya están cargados en memoria, igual que en /admin/casos) en vez de ir al servidor.
function BuscadorCaso({
  casos,
  casoId,
  onChange,
}: {
  casos: CasoAdmin[];
  casoId: string;
  onChange: (id: string) => void;
}) {
  const casoSeleccionado = useMemo(() => casos.find((c) => String(c.id) === casoId) ?? null, [casos, casoId]);
  const [texto, setTexto] = useState("");
  const [abierto, setAbierto] = useState(false);
  const [montado, setMontado] = useState(false);
  const [posicion, setPosicion] = useState({ top: 0, left: 0, width: 0 });
  const envolturaRef = useRef<HTMLDivElement>(null);

  // El portal (ver más abajo) solo puede renderizar a document.body una vez el componente
  // está montado en el navegador -- en el primer render del lado del servidor document no
  // existe. Sin este estado, Next.js lanza un error de hidratación.
  useEffect(() => {
    setMontado(true);
  }, []);

  // Recalcula la posición real en pantalla del campo cada vez que se abre el desplegable, y
  // la mantiene al día si la página hace scroll o cambia de tamaño mientras está abierto --
  // necesario porque, al vivir en un portal fuera de la tarjeta, ya no se posiciona solo con
  // CSS relativo a su contenedor.
  useEffect(() => {
    if (!abierto) return;
    function actualizarPosicion() {
      const rect = envolturaRef.current?.getBoundingClientRect();
      if (!rect) return;
      setPosicion({ top: rect.bottom + 4, left: rect.left, width: rect.width });
    }
    actualizarPosicion();
    window.addEventListener("scroll", actualizarPosicion, true);
    window.addEventListener("resize", actualizarPosicion);
    return () => {
      window.removeEventListener("scroll", actualizarPosicion, true);
      window.removeEventListener("resize", actualizarPosicion);
    };
  }, [abierto]);

  function etiqueta(c: CasoAdmin) {
    return `${c.numeroCaso ?? `Caso #${c.id}`} — ${c.nombreCliente}`;
  }

  // Mientras el desplegable está cerrado, el campo muestra la etiqueta del caso elegido (o
  // queda vacío si no hay ninguno) -- al abrirlo para buscar de nuevo, se limpia para no
  // obligar a borrar el texto anterior a mano.
  useEffect(() => {
    if (!abierto) {
      setTexto(casoSeleccionado ? etiqueta(casoSeleccionado) : "");
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [casoSeleccionado, abierto]);

  const resultados = useMemo(() => {
    const q = texto.trim().toLowerCase();
    const candidatos = casoSeleccionado && texto === etiqueta(casoSeleccionado) ? casos : casos.filter((c) => {
      if (!q) return true;
      return (
        (c.numeroCaso ?? "").toLowerCase().includes(q) ||
        c.nombreCliente.toLowerCase().includes(q) ||
        (c.radicadoId ?? "").toLowerCase().includes(q)
      );
    });
    return candidatos.slice(0, 8);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [casos, texto]);

  function elegir(id: string) {
    onChange(id);
    setAbierto(false);
  }

  return (
    <div className="relative" ref={envolturaRef}>
      <div className="relative">
        <MagnifyingGlass className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-soft" />
        <input
          value={texto}
          onChange={(e) => {
            setTexto(e.target.value);
            setAbierto(true);
          }}
          onFocus={(e) => {
            setAbierto(true);
            e.target.select();
          }}
          onBlur={() => setTimeout(() => setAbierto(false), 150)}
          placeholder="Buscar por número de caso, radicado o cliente..."
          className="w-full rounded-lg border border-line bg-paper px-10 py-2.5 text-sm text-ink outline-none focus:border-gold"
        />
        {casoSeleccionado && (
          <button
            type="button"
            tabIndex={-1}
            onMouseDown={(e) => e.preventDefault()}
            onClick={() => {
              onChange("");
              setTexto("");
            }}
            aria-label="Quitar caso seleccionado"
            className="absolute right-3 top-1/2 flex h-5 w-5 -translate-y-1/2 items-center justify-center rounded-full text-ink-soft hover:bg-ink/10 hover:text-ink"
          >
            <X className="h-3.5 w-3.5" weight="bold" />
          </button>
        )}
      </div>

      {/* Bug real encontrado: Chrome pintaba mal este listado cuando quedaba flotando
          encima de la tarjeta "2. Abogados" -- el filo dorado de esa tarjeta se veía
          encima del listado y partes de los resultados quedaban en blanco, aunque el DOM
          (confirmado con elementFromPoint/getComputedStyle en vivo) siempre tuvo el botón
          correcto ahí. Era un glitch de pintado/compositing de Chromium con un <div>
          absolutamente posicionado y alto (con scroll) justo encima de otra tarjeta con su
          propio box-shadow -- ni separar overflow-hidden/overflow-y-auto en dos capas ni
          forzar una capa GPU propia lo resolvieron del todo. La solución que sí funciona de
          raíz es la misma que ya usan los modales de este panel (ver Dialog de @base-ui/
          react en TareasDeCasoModal, ModalNuevoCaso, etc.): sacar el panel flotante del
          árbol de las tarjetas por completo con un portal a document.body, posicionado con
          coordenadas reales de pantalla (position:fixed) en vez de relativas a un padre que
          comparte espacio visual con otras tarjetas. */}
      {abierto &&
        montado &&
        createPortal(
          <div
            className="fixed z-50 max-h-80 overflow-y-auto rounded-lg border border-line bg-surface shadow-lg"
            style={{ top: posicion.top, left: posicion.left, width: posicion.width }}
          >
            <button
              type="button"
              onMouseDown={(e) => e.preventDefault()}
              onClick={() => elegir("")}
              className="flex w-full items-center px-4 py-2.5 text-left text-sm text-ink-soft hover:bg-ink/5"
            >
              Sin caso asociado
            </button>
            {resultados.length === 0 ? (
              <p className="px-4 py-3 text-sm text-ink-soft">Ningún caso coincide con "{texto}".</p>
            ) : (
              resultados.map((c) => (
                <button
                  key={c.id}
                  type="button"
                  onMouseDown={(e) => e.preventDefault()}
                  onClick={() => elegir(String(c.id))}
                  className="flex w-full flex-col items-start gap-0.5 border-t border-line px-4 py-2.5 text-left text-sm first:border-t-0 hover:bg-ink/5"
                >
                  <span className="font-medium text-ink">{c.numeroCaso ?? `Caso #${c.id}`}</span>
                  <span className="text-xs text-ink-soft">{c.nombreCliente}</span>
                </button>
              ))
            )}
            {texto.trim().length === 0 && casos.length > resultados.length && (
              <p className="border-t border-line px-4 py-2 text-xs text-ink-soft">
                Mostrando {resultados.length} de {casos.length} casos — sigue escribiendo para filtrar.
              </p>
            )}
          </div>,
          document.body,
        )}
    </div>
  );
}
