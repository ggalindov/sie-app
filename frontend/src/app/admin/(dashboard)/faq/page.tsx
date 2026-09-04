"use client";

import { useEffect, useState } from "react";
import { toast } from "sonner";
import {
  listarFaqCandidatas,
  moderarFaq,
  ApiError,
  type PreguntaFrecuenteAdmin,
} from "@/lib/admin-api";
import { AdminPageHeader, AdminButton, AdminCard, Badge, EmptyState, AdminLoader } from "@/components/admin/ui";

function formatearFecha(iso: string) {
  return new Date(iso).toLocaleDateString("es-CO", { day: "numeric", month: "short", year: "numeric" });
}

export default function FaqAdminPage() {
  const [preguntas, setPreguntas] = useState<PreguntaFrecuenteAdmin[] | null>(null);
  const [respuestas, setRespuestas] = useState<Record<number, string>>({});
  const [procesando, setProcesando] = useState<number | null>(null);

  useEffect(() => {
    cargar();
  }, []);

  function cargar() {
    listarFaqCandidatas()
      .then((datos) => {
        setPreguntas(datos);
        setRespuestas((prev) => {
          const siguiente = { ...prev };
          datos.forEach((p) => {
            if (siguiente[p.id] === undefined) {
              siguiente[p.id] = p.respuestaSugerida ?? "";
            }
          });
          return siguiente;
        });
      })
      .catch(() => toast.error("No se pudieron cargar las preguntas frecuentes."));
  }

  async function onAprobar(id: number) {
    const respuesta = (respuestas[id] ?? "").trim();
    if (!respuesta) {
      toast.error("Escribe una respuesta antes de aprobar.");
      return;
    }
    setProcesando(id);
    try {
      await moderarFaq(id, "APROBADA", respuesta);
      setPreguntas((prev) => prev?.filter((p) => p.id !== id) ?? null);
      toast.success("Pregunta aprobada y publicada.");
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo aprobar la pregunta.");
    } finally {
      setProcesando(null);
    }
  }

  async function onDescartar(id: number) {
    setProcesando(id);
    try {
      await moderarFaq(id, "RECHAZADA");
      setPreguntas((prev) => prev?.filter((p) => p.id !== id) ?? null);
      toast.success("Pregunta descartada.");
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo descartar la pregunta.");
    } finally {
      setProcesando(null);
    }
  }

  return (
    <div>
      <AdminPageHeader
        title="Preguntas frecuentes"
        description="Preguntas que el chatbot recibió varias veces. Escribe la respuesta final y apruébala para publicarla en el sitio."
      />

      {preguntas === null ? (
        <AdminLoader />
      ) : preguntas.length === 0 ? (
        <EmptyState
          title="No hay candidatas por ahora"
          description="Cuando una pregunta se repita lo suficiente en el chatbot, aparecerá aquí."
        />
      ) : (
        <div className="space-y-3">
          {preguntas.map((p) => (
            <AdminCard key={p.id}>
              <div className="flex flex-wrap items-center gap-2">
                <Badge tone="gold">{p.conteo}× preguntada</Badge>
                <span className="text-xs text-ink-soft">Desde {formatearFecha(p.fechaPrimeraVez)}</span>
              </div>
              <p className="mt-2 font-medium text-ink">{p.preguntaEjemplo}</p>

              <div className="mt-3 space-y-2">
                <label htmlFor={`respuesta-${p.id}`} className="text-xs font-medium text-ink-soft">
                  Respuesta final (editable)
                </label>
                <textarea
                  id={`respuesta-${p.id}`}
                  rows={4}
                  value={respuestas[p.id] ?? ""}
                  onChange={(e) => setRespuestas((prev) => ({ ...prev, [p.id]: e.target.value }))}
                  placeholder="Escribe la respuesta que se mostrará públicamente"
                  className="w-full rounded-2xl border border-line bg-paper px-4 py-3 text-sm leading-relaxed text-ink focus:border-gold-deep focus:outline-none"
                />
              </div>

              <div className="mt-4 flex justify-end gap-2">
                <AdminButton variant="danger" disabled={procesando === p.id} onClick={() => onDescartar(p.id)}>
                  Descartar
                </AdminButton>
                <AdminButton disabled={procesando === p.id} onClick={() => onAprobar(p.id)}>
                  Aprobar y publicar
                </AdminButton>
              </div>
            </AdminCard>
          ))}
        </div>
      )}
    </div>
  );
}
