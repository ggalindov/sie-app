"use client";

import { useEffect, useState } from "react";
import { CaretLeft, CaretRight } from "@phosphor-icons/react";
import { contarPaginasDocumento, urlPaginaDocumento } from "@/lib/firma-publica-api";

// Vista previa SIN descarga: el firmante nunca recibe el PDF real antes de firmar, solo
// cada página renderizada como imagen con marca de agua "SOLO LECTURA" (ver
// DocumentoVistaPreviaService en el backend). Evita que alguien reenvíe el documento sin
// firmar, o lo edite, antes de completar el proceso.
export function VisorDocumentoFirma({ token }: { token: string }) {
  const [totalPaginas, setTotalPaginas] = useState<number | null>(null);
  const [pagina, setPagina] = useState(1);
  const [error, setError] = useState(false);

  useEffect(() => {
    contarPaginasDocumento(token)
      .then(setTotalPaginas)
      .catch(() => setError(true));
  }, [token]);

  if (error) {
    return <p className="text-sm text-red-700">No se pudo cargar la vista previa del documento.</p>;
  }
  if (totalPaginas === null) {
    return <p className="text-sm text-ink-soft">Cargando documento...</p>;
  }

  return (
    <div>
      <div className="overflow-hidden rounded-xl border border-line bg-ink/[0.02]">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src={urlPaginaDocumento(token, pagina)} alt={`Página ${pagina} del documento`} className="w-full" />
      </div>
      <div className="mt-3 flex items-center justify-between">
        <button
          type="button"
          onClick={() => setPagina((p) => Math.max(1, p - 1))}
          disabled={pagina <= 1}
          className="flex h-9 w-9 items-center justify-center rounded-full bg-ink/5 text-ink disabled:opacity-30"
          aria-label="Página anterior"
        >
          <CaretLeft className="h-4 w-4" weight="bold" />
        </button>
        <span className="text-sm text-ink-soft">
          Página {pagina} de {totalPaginas}
        </span>
        <button
          type="button"
          onClick={() => setPagina((p) => Math.min(totalPaginas, p + 1))}
          disabled={pagina >= totalPaginas}
          className="flex h-9 w-9 items-center justify-center rounded-full bg-ink/5 text-ink disabled:opacity-30"
          aria-label="Página siguiente"
        >
          <CaretRight className="h-4 w-4" weight="bold" />
        </button>
      </div>
    </div>
  );
}
