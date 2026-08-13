"use client";

import { use, useEffect, useState } from "react";
import { toast } from "sonner";
import { obtenerArticuloAdmin, type ArticuloAdmin } from "@/lib/admin-api";
import { AdminPageHeader } from "@/components/admin/ui";
import { ArticuloForm } from "@/components/admin/articulo-form";

export default function EditarArticuloPage({ params }: PageProps<"/admin/articulos/[id]/editar">) {
  const { id } = use(params);
  const [articulo, setArticulo] = useState<ArticuloAdmin | null>(null);
  const [error, setError] = useState(false);

  useEffect(() => {
    obtenerArticuloAdmin(Number(id))
      .then(setArticulo)
      .catch(() => {
        setError(true);
        toast.error("No se pudo cargar el artículo.");
      });
  }, [id]);

  if (error) {
    return <p className="text-sm text-ink-soft">No encontramos ese artículo.</p>;
  }

  if (!articulo) {
    return <p className="text-sm text-ink-soft">Cargando...</p>;
  }

  return (
    <div>
      <AdminPageHeader title="Editar artículo" />
      <ArticuloForm articulo={articulo} />
    </div>
  );
}
