"use client";

import { useRef, useState } from "react";
import { motion } from "motion/react";
import {
  MagnifyingGlass,
  UploadSimple,
  ShieldCheck,
  ShieldWarning,
  ShieldSlash,
  Spinner,
} from "@phosphor-icons/react";
import {
  verificarDocumentoPorCodigo,
  verificarDocumentoPorPdf,
  ApiError,
  type VerificacionPublica,
} from "@/lib/api";

const EASE = [0.16, 1, 0.3, 1] as const;

function formatearFecha(iso: string | null) {
  if (!iso) return "—";
  try {
    return new Date(iso).toLocaleDateString("es-CO", { day: "numeric", month: "long", year: "numeric", timeZone: "America/Bogota" });
  } catch {
    return iso;
  }
}

export default function VerificarDocumentoPage() {
  const [modo, setModo] = useState<"codigo" | "pdf">("codigo");
  const [codigo, setCodigo] = useState("");
  const [cargando, setCargando] = useState(false);
  const [resultado, setResultado] = useState<VerificacionPublica | null>(null);
  const [error, setError] = useState<string | null>(null);
  const inputArchivoRef = useRef<HTMLInputElement>(null);

  async function consultarPorCodigo() {
    if (!codigo.trim()) return;
    setCargando(true);
    setError(null);
    setResultado(null);
    try {
      setResultado(await verificarDocumentoPorCodigo(codigo.trim()));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "No se pudo consultar el código.");
    } finally {
      setCargando(false);
    }
  }

  async function consultarPorArchivo(archivo: File) {
    setCargando(true);
    setError(null);
    setResultado(null);
    try {
      setResultado(await verificarDocumentoPorPdf(archivo));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "No se pudo verificar el archivo.");
    } finally {
      setCargando(false);
    }
  }

  return (
    <main className="relative flex-1 overflow-hidden pt-28 pb-24 md:pt-36 md:pb-32">
      <div
        aria-hidden="true"
        className="pointer-events-none absolute inset-x-0 top-0 h-[480px] -z-10 select-none opacity-60"
        style={{
          background:
            "radial-gradient(70% 60% at 50% 0%, rgba(217,169,37,0.14) 0%, rgba(217,169,37,0.03) 45%, transparent 75%)",
        }}
      />

      <div className="mx-auto max-w-2xl px-4 sm:px-6 lg:px-8">
        <motion.div
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.5, ease: EASE }}
          className="text-center"
        >
          <p className="text-xs font-medium uppercase tracking-[0.18em] text-gold-deep">Motor de firma electrónica</p>
          <h1 className="mt-3 font-display text-3xl tracking-tight text-ink sm:text-4xl">Verifica un documento firmado</h1>
          <p className="mt-3 text-ink-soft">
            Consulta el código de verificación impreso en el documento, o sube el archivo PDF para comprobar que no
            ha sido alterado desde que SIE Jurídicos lo selló.
          </p>
        </motion.div>

        <div className="mt-8 flex justify-center gap-2">
          <button
            type="button"
            onClick={() => setModo("codigo")}
            className={`rounded-full px-4 py-2 text-sm font-medium transition-colors ${
              modo === "codigo" ? "bg-ink text-paper" : "bg-ink/5 text-ink-soft hover:bg-ink/10"
            }`}
          >
            Por código
          </button>
          <button
            type="button"
            onClick={() => setModo("pdf")}
            className={`rounded-full px-4 py-2 text-sm font-medium transition-colors ${
              modo === "pdf" ? "bg-ink text-paper" : "bg-ink/5 text-ink-soft hover:bg-ink/10"
            }`}
          >
            Subiendo el PDF
          </button>
        </div>

        <div className="mt-6 rounded-2xl bg-surface p-6 ring-1 ring-line">
          {modo === "codigo" ? (
            <div className="flex gap-2">
              <input
                value={codigo}
                onChange={(e) => setCodigo(e.target.value)}
                onKeyDown={(e) => e.key === "Enter" && consultarPorCodigo()}
                placeholder="Código de verificación"
                className="flex-1 rounded-lg border border-line bg-paper px-4 py-3 text-sm text-ink outline-none focus:border-gold"
              />
              <button
                type="button"
                onClick={consultarPorCodigo}
                disabled={cargando}
                className="flex items-center gap-2 rounded-lg bg-gold px-4 py-3 text-sm font-semibold text-ink-fixed disabled:opacity-60"
              >
                {cargando ? <Spinner className="h-4 w-4 animate-spin" weight="bold" /> : <MagnifyingGlass className="h-4 w-4" weight="bold" />}
                Verificar
              </button>
            </div>
          ) : (
            <label className="flex cursor-pointer flex-col items-center justify-center gap-2 rounded-xl border border-dashed border-line bg-paper px-4 py-10 text-center text-sm text-ink-soft hover:border-gold">
              {cargando ? <Spinner className="h-5 w-5 animate-spin" weight="bold" /> : <UploadSimple className="h-5 w-5" weight="light" />}
              {cargando ? "Verificando..." : "Haz clic para elegir el archivo PDF"}
              <input
                ref={inputArchivoRef}
                type="file"
                accept="application/pdf"
                className="hidden"
                disabled={cargando}
                onChange={(e) => {
                  const archivo = e.target.files?.[0];
                  if (archivo) consultarPorArchivo(archivo);
                }}
              />
            </label>
          )}
        </div>

        {error && <p className="mt-4 text-center text-sm text-red-700">{error}</p>}

        {resultado && (
          <motion.div
            initial={{ opacity: 0, y: 10 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.4, ease: EASE }}
            className="mt-6 rounded-2xl bg-surface p-6 ring-1 ring-line"
          >
            <VeredictoEncabezado resultado={resultado} />

            {resultado.nombreDocumento && (
              <div className="mt-5 space-y-2 text-sm">
                <Fila etiqueta="Documento" valor={resultado.nombreDocumento} />
                <Fila etiqueta="Emitido" valor={formatearFecha(resultado.fechaEmision)} />
                {resultado.hashSha256 && <Fila etiqueta="Huella SHA-256" valor={resultado.hashSha256} mono />}
                {resultado.selloTiempo && (
                  <Fila
                    etiqueta="Sello de tiempo"
                    valor={resultado.selloTiempo.aplicado ? `Aplicado · ${resultado.selloTiempo.autoridad ?? ""}` : "No aplicado"}
                  />
                )}
              </div>
            )}

            {resultado.firmantes && resultado.firmantes.length > 0 && (
              <div className="mt-5">
                <p className="text-xs font-medium uppercase tracking-wide text-ink-soft">Firmantes</p>
                <div className="mt-2 space-y-2">
                  {resultado.firmantes.map((f, i) => (
                    <div key={i} className="flex items-center justify-between rounded-lg bg-ink/[0.03] px-3 py-2 text-sm">
                      <span>
                        {f.nombreEnmascarado} <span className="text-xs text-ink-soft">({f.rol})</span>
                      </span>
                      <span className="text-xs text-ink-soft">{f.estado}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {resultado.hallazgos.length > 0 && (
              <div className="mt-5 rounded-lg bg-red-50 p-4 text-sm text-red-800">
                <ul className="list-disc space-y-1 pl-4">
                  {resultado.hallazgos.map((h, i) => (
                    <li key={i}>{h}</li>
                  ))}
                </ul>
              </div>
            )}
          </motion.div>
        )}
      </div>
    </main>
  );
}

function VeredictoEncabezado({ resultado }: { resultado: VerificacionPublica }) {
  if (resultado.veredicto === "INTEGRO") {
    return (
      <div className="flex items-start gap-3">
        <ShieldCheck className="h-8 w-8 shrink-0 text-emerald-600" weight="fill" />
        <div>
          <p className="font-display text-lg text-ink">Documento íntegro</p>
          <p className="mt-1 text-sm text-ink-soft">{resultado.detalleLegal}</p>
        </div>
      </div>
    );
  }
  if (resultado.veredicto === "ALTERADO") {
    return (
      <div className="flex items-start gap-3">
        <ShieldWarning className="h-8 w-8 shrink-0 text-red-600" weight="fill" />
        <div>
          <p className="font-display text-lg text-ink">Señales de alteración</p>
          <p className="mt-1 text-sm text-ink-soft">{resultado.detalleLegal}</p>
        </div>
      </div>
    );
  }
  return (
    <div className="flex items-start gap-3">
      <ShieldSlash className="h-8 w-8 shrink-0 text-ink-soft" weight="fill" />
      <div>
        <p className="font-display text-lg text-ink">No encontrado</p>
        <p className="mt-1 text-sm text-ink-soft">{resultado.detalleLegal}</p>
      </div>
    </div>
  );
}

function Fila({ etiqueta, valor, mono }: { etiqueta: string; valor: string; mono?: boolean }) {
  return (
    <div className="flex items-start justify-between gap-4">
      <span className="shrink-0 text-ink-soft">{etiqueta}</span>
      <span className={`text-right text-ink ${mono ? "break-all font-mono text-xs" : ""}`}>{valor}</span>
    </div>
  );
}
