"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import {
  MagnifyingGlass,
  Briefcase,
  AddressBook,
  Envelope,
  FileText,
  Spinner,
} from "@phosphor-icons/react";
import { buscarGlobal, type ResultadoBusqueda, type TipoResultadoBusqueda } from "@/lib/admin-api";

// Buscador total del panel (pedido explícito del usuario: "buscar nombre... o por
// radicado... toda la búsqueda"), visible para ADMIN_GENERAL y ABOGADO por igual -- los 4
// módulos que cubre el backend (ver BusquedaService) ya son visibles para ambos roles, así
// que no hace falta ningún filtro de rol aquí.
//
// Mismo patrón de debounce que ya usa el buscador de CRM (crm/page.tsx: dos estados,
// inputBusqueda/busqueda, 350ms) para no mandar una petición al backend por cada tecla.

const ETIQUETAS: Record<TipoResultadoBusqueda, string> = {
  CASO: "Casos",
  CLIENTE: "Clientes CRM",
  SOLICITUD: "Solicitudes",
  DOCUMENTO: "Documentos para firma",
};

const ICONOS: Record<TipoResultadoBusqueda, typeof Briefcase> = {
  CASO: Briefcase,
  CLIENTE: AddressBook,
  SOLICITUD: Envelope,
  DOCUMENTO: FileText,
};

const ORDEN_TIPOS: TipoResultadoBusqueda[] = ["CASO", "CLIENTE", "SOLICITUD", "DOCUMENTO"];

export function BuscadorGlobal() {
  const router = useRouter();
  const contenedorRef = useRef<HTMLDivElement | null>(null);
  const [inputBusqueda, setInputBusqueda] = useState("");
  const [busqueda, setBusqueda] = useState("");
  const [resultados, setResultados] = useState<ResultadoBusqueda[] | null>(null);
  const [cargando, setCargando] = useState(false);
  const [abierto, setAbierto] = useState(false);

  useEffect(() => {
    const t = setTimeout(() => setBusqueda(inputBusqueda.trim()), 350);
    return () => clearTimeout(t);
  }, [inputBusqueda]);

  useEffect(() => {
    if (busqueda.length < 2) {
      setResultados(null);
      setCargando(false);
      return;
    }
    setCargando(true);
    let cancelado = false;
    buscarGlobal(busqueda)
      .then((r) => {
        if (!cancelado) setResultados(r);
      })
      .catch(() => {
        if (!cancelado) setResultados([]);
      })
      .finally(() => {
        if (!cancelado) setCargando(false);
      });
    return () => {
      cancelado = true;
    };
  }, [busqueda]);

  useEffect(() => {
    function alHacerClicAfuera(e: MouseEvent) {
      if (contenedorRef.current && !contenedorRef.current.contains(e.target as Node)) {
        setAbierto(false);
      }
    }
    function alPresionarEscape(e: KeyboardEvent) {
      if (e.key === "Escape") setAbierto(false);
    }
    document.addEventListener("mousedown", alHacerClicAfuera);
    document.addEventListener("keydown", alPresionarEscape);
    return () => {
      document.removeEventListener("mousedown", alHacerClicAfuera);
      document.removeEventListener("keydown", alPresionarEscape);
    };
  }, []);

  function irAResultado(r: ResultadoBusqueda) {
    setAbierto(false);
    setInputBusqueda("");
    router.push(r.ruta);
  }

  const agrupados = ORDEN_TIPOS.map((tipo) => ({
    tipo,
    items: (resultados ?? []).filter((r) => r.tipo === tipo),
  })).filter((g) => g.items.length > 0);

  const mostrarPanel = abierto && busqueda.length >= 2;

  return (
    <div ref={contenedorRef} className="relative w-full max-w-md">
      <div className="relative">
        <MagnifyingGlass className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-soft" weight="bold" />
        <input
          value={inputBusqueda}
          onChange={(e) => setInputBusqueda(e.target.value)}
          onFocus={() => setAbierto(true)}
          placeholder="Buscar por nombre o radicado en todo el sistema..."
          className="w-full rounded-xl border border-line bg-paper py-2 pl-9 pr-3 text-sm outline-none focus:ring-2 focus:ring-gold"
        />
        {cargando && (
          <Spinner className="absolute right-3 top-1/2 h-4 w-4 -translate-y-1/2 animate-spin text-ink-soft" weight="bold" />
        )}
      </div>

      {mostrarPanel && (
        <div className="absolute left-0 right-0 top-[calc(100%+6px)] z-30 max-h-[70vh] overflow-y-auto rounded-xl border border-line bg-surface shadow-lg">
          {resultados === null || cargando ? (
            <p className="px-4 py-4 text-sm text-ink-soft">Buscando...</p>
          ) : agrupados.length === 0 ? (
            <p className="px-4 py-4 text-sm text-ink-soft">Sin resultados para &quot;{busqueda}&quot;.</p>
          ) : (
            agrupados.map((grupo) => {
              const Icono = ICONOS[grupo.tipo];
              return (
                <div key={grupo.tipo} className="border-b border-line last:border-b-0">
                  <p className="px-4 pt-3 pb-1 text-[11px] font-semibold uppercase tracking-[0.1em] text-ink-soft">
                    {ETIQUETAS[grupo.tipo]}
                  </p>
                  {grupo.items.map((r) => (
                    <button
                      key={`${r.tipo}-${r.id}`}
                      type="button"
                      onClick={() => irAResultado(r)}
                      className="flex w-full items-center gap-3 px-4 py-2.5 text-left hover:bg-ink/5"
                    >
                      <Icono className="h-4 w-4 shrink-0 text-gold-deep" weight="light" />
                      <span className="min-w-0">
                        <span className="block truncate text-sm font-medium text-ink">{r.titulo}</span>
                        {r.subtitulo && <span className="block truncate text-xs text-ink-soft">{r.subtitulo}</span>}
                      </span>
                    </button>
                  ))}
                </div>
              );
            })
          )}
        </div>
      )}
    </div>
  );
}
