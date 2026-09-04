"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { toast } from "sonner";
import { Plus, PencilSimple, Trash, Eye } from "@phosphor-icons/react";
import { listarArticulosAdmin, eliminarArticulo, ApiError, type ArticuloAdmin } from "@/lib/admin-api";
import { AdminPageHeader, AdminButton, AdminCard, Badge, EmptyState, AdminLoader } from "@/components/admin/ui";
import { CompartirArticuloButton } from "@/components/admin/compartir-articulo-button";

function formatearFecha(iso: string) {
  return new Date(iso).toLocaleDateString("es-CO", { day: "numeric", month: "short", year: "numeric" });
}

export default function ArticulosPage() {
  const [articulos, setArticulos] = useState<ArticuloAdmin[] | null>(null);
  const [eliminandoId, setEliminandoId] = useState<number | null>(null);

  useEffect(() => {
    cargar();
  }, []);

  function cargar() {
    listarArticulosAdmin()
      .then(setArticulos)
      .catch(() => toast.error("No se pudieron cargar los artículos."));
  }

  async function onEliminar(id: number, titulo: string) {
    if (!window.confirm(`¿Eliminar "${titulo}"? Esta acción no se puede deshacer.`)) return;
    setEliminandoId(id);
    try {
      await eliminarArticulo(id);
      setArticulos((prev) => prev?.filter((a) => a.id !== id) ?? null);
      toast.success("Artículo eliminado.");
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo eliminar el artículo.");
    } finally {
      setEliminandoId(null);
    }
  }

  return (
    <div>
      <AdminPageHeader
        title="Blog y Noticias"
        description="Artículos y noticias, publicados y en borrador."
        action={
          <Link href="/admin/articulos/nuevo">
            <AdminButton>
              <Plus className="h-4 w-4" weight="bold" />
              Nuevo artículo
            </AdminButton>
          </Link>
        }
      />

      {articulos === null ? (
        <AdminLoader />
      ) : articulos.length === 0 ? (
        <EmptyState title="Aún no hay artículos" description="Crea el primero desde el botón de arriba." />
      ) : (
        <div className="space-y-3">
          {articulos.map((a) => (
            <AdminCard key={a.id} className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  <p className="font-medium text-ink">{a.titulo}</p>
                  <Badge tone={a.estado === "PUBLICADO" ? "success" : "neutral"}>{a.estado}</Badge>
                  {a.tipoContenido === "NOTICIA" && <Badge tone="gold">Noticia</Badge>}
                </div>
                <p className="mt-1 text-xs text-ink-soft">
                  {a.categoria.nombre} · {a.autorNombre} · {formatearFecha(a.fechaCreacion)}
                </p>
              </div>

              <div className="flex shrink-0 items-center gap-2">
                {a.estado === "PUBLICADO" && (
                  <>
                    <a
                      href={`/blog/${a.slug}`}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="flex h-9 w-9 items-center justify-center rounded-full text-ink-soft hover:bg-ink/5"
                      aria-label="Ver en el sitio"
                    >
                      <Eye className="h-4 w-4" weight="light" />
                    </a>
                    <CompartirArticuloButton
                      titulo={a.titulo}
                      slug={a.slug}
                      resumen={a.resumen}
                      categoria={a.categoria.nombre}
                      esNoticia={a.tipoContenido === "NOTICIA"}
                    />
                  </>
                )}
                <Link
                  href={`/admin/articulos/${a.id}/editar`}
                  className="flex h-9 w-9 items-center justify-center rounded-full text-ink-soft hover:bg-ink/5"
                  aria-label="Editar"
                >
                  <PencilSimple className="h-4 w-4" weight="light" />
                </Link>
                <button
                  type="button"
                  disabled={eliminandoId === a.id}
                  onClick={() => onEliminar(a.id, a.titulo)}
                  className="flex h-9 w-9 items-center justify-center rounded-full text-ink-soft hover:bg-red-50 hover:text-red-700"
                  aria-label="Eliminar"
                >
                  <Trash className="h-4 w-4" weight="light" />
                </button>
              </div>
            </AdminCard>
          ))}
        </div>
      )}
    </div>
  );
}
