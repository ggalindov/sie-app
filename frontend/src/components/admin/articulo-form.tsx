"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { toast } from "sonner";
import type { Categoria } from "@/lib/api";
import {
  crearArticulo,
  actualizarArticulo,
  listarCategorias,
  ApiError,
  type ArticuloAdmin,
} from "@/lib/admin-api";
import { AdminButton } from "@/components/admin/ui";
import { RichTextEditor } from "@/components/admin/rich-text-editor";

export function ArticuloForm({ articulo }: { articulo?: ArticuloAdmin }) {
  const router = useRouter();
  const [categorias, setCategorias] = useState<Categoria[]>([]);
  const [titulo, setTitulo] = useState(articulo?.titulo ?? "");
  const [idCategoria, setIdCategoria] = useState<number | "">(articulo?.categoria.id ?? "");
  const [resumen, setResumen] = useState(articulo?.resumen ?? "");
  const [contenido, setContenido] = useState(articulo?.contenido ?? "");
  const [tiempoLecturaMin, setTiempoLecturaMin] = useState(articulo?.tiempoLecturaMin?.toString() ?? "");
  const [guardando, setGuardando] = useState<"borrador" | "publicado" | null>(null);

  useEffect(() => {
    listarCategorias().then(setCategorias).catch(() => toast.error("No se pudieron cargar las categorías."));
  }, []);

  function validar(): string | null {
    if (!titulo.trim()) return "El título es obligatorio.";
    if (!idCategoria) return "Selecciona una categoría.";
    if (!contenido.trim() || contenido === "<p></p>") return "El contenido es obligatorio.";
    return null;
  }

  async function guardar(estadoDestino: "BORRADOR" | "PUBLICADO") {
    const errorValidacion = validar();
    if (errorValidacion) {
      toast.error(errorValidacion);
      return;
    }

    setGuardando(estadoDestino === "PUBLICADO" ? "publicado" : "borrador");
    const input = {
      titulo: titulo.trim(),
      contenido,
      resumen: resumen.trim(),
      idCategoria: Number(idCategoria),
      tiempoLecturaMin: tiempoLecturaMin ? Number(tiempoLecturaMin) : null,
    };

    try {
      if (articulo) {
        await actualizarArticulo(articulo.id, { ...input, estado: estadoDestino });
      } else if (estadoDestino === "BORRADOR") {
        await crearArticulo(input);
      } else {
        const creado = await crearArticulo(input);
        await actualizarArticulo(creado.id, { ...input, estado: "PUBLICADO" });
      }
      toast.success(estadoDestino === "PUBLICADO" ? "Artículo publicado." : "Borrador guardado.");
      router.push("/admin/articulos");
      router.refresh();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo guardar el artículo.");
    } finally {
      setGuardando(null);
    }
  }

  return (
    <div className="space-y-6">
      <div className="space-y-2">
        <label htmlFor="titulo" className="text-sm font-medium text-ink">
          Título
        </label>
        <input
          id="titulo"
          value={titulo}
          onChange={(e) => setTitulo(e.target.value)}
          className="w-full rounded-xl border border-line bg-surface px-4 py-3 text-sm text-ink focus:border-gold-deep focus:outline-none"
          placeholder="Título del artículo"
        />
      </div>

      <div className="grid gap-5 sm:grid-cols-2">
        <div className="space-y-2">
          <label htmlFor="categoria" className="text-sm font-medium text-ink">
            Categoría
          </label>
          <select
            id="categoria"
            value={idCategoria}
            onChange={(e) => setIdCategoria(e.target.value ? Number(e.target.value) : "")}
            className="w-full rounded-xl border border-line bg-surface px-4 py-3 text-sm text-ink focus:border-gold-deep focus:outline-none"
          >
            <option value="">Selecciona una categoría</option>
            {categorias.map((c) => (
              <option key={c.id} value={c.id}>
                {c.nombre}
              </option>
            ))}
          </select>
        </div>
        <div className="space-y-2">
          <label htmlFor="tiempo" className="text-sm font-medium text-ink">
            Tiempo de lectura (min)
          </label>
          <input
            id="tiempo"
            type="number"
            min={1}
            value={tiempoLecturaMin}
            onChange={(e) => setTiempoLecturaMin(e.target.value)}
            className="w-full rounded-xl border border-line bg-surface px-4 py-3 text-sm text-ink focus:border-gold-deep focus:outline-none"
            placeholder="6"
          />
        </div>
      </div>

      <div className="space-y-2">
        <label htmlFor="resumen" className="text-sm font-medium text-ink">
          Resumen
        </label>
        <textarea
          id="resumen"
          rows={2}
          value={resumen}
          onChange={(e) => setResumen(e.target.value)}
          className="w-full resize-none rounded-xl border border-line bg-surface px-4 py-3 text-sm text-ink focus:border-gold-deep focus:outline-none"
          placeholder="Aparece en el listado del blog"
        />
      </div>

      <div className="space-y-2">
        <label className="text-sm font-medium text-ink">Contenido</label>
        <RichTextEditor value={contenido} onChange={setContenido} />
      </div>

      <div className="flex flex-wrap gap-3 pt-2">
        <AdminButton variant="ghost" disabled={!!guardando} onClick={() => guardar("BORRADOR")}>
          {guardando === "borrador" ? "Guardando..." : "Guardar borrador"}
        </AdminButton>
        <AdminButton disabled={!!guardando} onClick={() => guardar("PUBLICADO")}>
          {guardando === "publicado" ? "Publicando..." : "Publicar"}
        </AdminButton>
      </div>
    </div>
  );
}
