"use client";

import { useEffect, useRef, useState } from "react";
import { toast } from "sonner";
import { AnimatePresence, motion } from "motion/react";
import { ShareNetwork, ClipboardText, WhatsappLogo, Check } from "@phosphor-icons/react";

const EASE = [0.16, 1, 0.3, 1] as const;

// Máximo de caracteres del resumen dentro del mensaje: un resumen completo (pensado para la
// tarjeta del blog, no para un mensaje de WhatsApp) puede ser un párrafo entero -- se recorta
// a una sola línea gancho, en un corte de palabra completa, nunca a mitad de una.
const LARGO_MAXIMO_RESUMEN = 110;

function recortarResumen(resumen: string): string {
  if (resumen.length <= LARGO_MAXIMO_RESUMEN) return resumen;
  const corte = resumen.slice(0, LARGO_MAXIMO_RESUMEN);
  const ultimoEspacio = corte.lastIndexOf(" ");
  return `${corte.slice(0, ultimoEspacio > 40 ? ultimoEspacio : LARGO_MAXIMO_RESUMEN)}…`;
}

// Un solo formato de texto para promocionar cualquier artículo (WhatsApp e Instagram: esta
// última no tiene ningún esquema de enlace para prellenar texto, solo se copia y pega a mano
// dentro de la app). Se arma con datos reales del artículo (categoría, resumen, si es
// noticia) en vez de una sola línea genérica -- pedido explícito del usuario: un cuerpo más
// robusto y llamativo para que a quien lo reciba le den ganas de entrar a leerlo.
//
// Emojis elegidos a propósito, todos de un solo punto de código sin selector de variación ni
// secuencia ZWJ (🚨 📖 👉 ✅): esa clase de emoji compuesto es la que a veces se rompe o cae a
// una imagen "tofu" en apps/teclados desactualizados -- justo el fallo reportado con la
// versión anterior de este mensaje. *título* usa el markdown propio de WhatsApp (negrita):
// si por lo que sea no se interpreta, los asteriscos igual se leen bien como texto plano, así
// que nunca rompe el mensaje.
function construirTextoPromocional(
    titulo: string,
    enlace: string,
    opciones: { resumen?: string | null; categoria?: string; esNoticia?: boolean } = {},
): string {
  const etiqueta = opciones.esNoticia ? "🚨 Noticia jurídica" : "📖 Nuevo en el blog";
  const categoriaTexto = opciones.categoria ? ` · ${opciones.categoria}` : "";
  const gancho = opciones.resumen ? `\n${recortarResumen(opciones.resumen)}\n` : "\n";
  return (
      `${etiqueta}${categoriaTexto}\n*${titulo}*\n${gancho}\n` +
      `En SIE Jurídicos lo explicamos claro y sin tecnicismos, con más de 20 años de experiencia legal a tu lado. ✅\n\n` +
      `👉 Léelo completo aquí:\n${enlace}`
  );
}

export function CompartirArticuloButton({
  titulo,
  slug,
  resumen,
  categoria,
  esNoticia,
}: {
  titulo: string;
  slug: string;
  resumen?: string | null;
  categoria?: string;
  esNoticia?: boolean;
}) {
  const [abierto, setAbierto] = useState(false);
  const [copiado, setCopiado] = useState(false);
  const wrapRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!abierto) return;
    function onPointerDown(e: PointerEvent) {
      if (wrapRef.current && !wrapRef.current.contains(e.target as Node)) setAbierto(false);
    }
    function onKeyDown(e: KeyboardEvent) {
      if (e.key === "Escape") setAbierto(false);
    }
    window.addEventListener("pointerdown", onPointerDown);
    window.addEventListener("keydown", onKeyDown);
    return () => {
      window.removeEventListener("pointerdown", onPointerDown);
      window.removeEventListener("keydown", onKeyDown);
    };
  }, [abierto]);

  // window.location.origin en vez de un dominio fijo: el mismo botón sirve tanto en local
  // (localhost:3000) como en producción (siejuridicos.com) sin necesitar configuración
  // aparte.
  function enlaceArticulo() {
    return `${window.location.origin}/blog/${slug}`;
  }

  async function onCopiarTexto() {
    try {
      await navigator.clipboard.writeText(
          construirTextoPromocional(titulo, enlaceArticulo(), { resumen, categoria, esNoticia }),
      );
      setCopiado(true);
      toast.success("Texto para compartir copiado. Pégalo en Instagram o donde quieras.");
      setTimeout(() => setCopiado(false), 2000);
    } catch {
      toast.error("No se pudo copiar el texto. Copia el enlace manualmente.");
    }
    setAbierto(false);
  }

  function onCompartirWhatsapp() {
    const texto = construirTextoPromocional(titulo, enlaceArticulo(), { resumen, categoria, esNoticia });
    window.open(`https://wa.me/?text=${encodeURIComponent(texto)}`, "_blank", "noopener,noreferrer");
    setAbierto(false);
  }

  return (
    <div ref={wrapRef} className="relative">
      <button
        type="button"
        onClick={() => setAbierto((v) => !v)}
        aria-label="Compartir en redes sociales"
        aria-expanded={abierto}
        className="flex h-9 w-9 items-center justify-center rounded-full text-ink-soft hover:bg-ink/5"
      >
        <ShareNetwork className="h-4 w-4" weight="light" />
      </button>

      <AnimatePresence>
        {abierto && (
          <motion.div
            initial={{ opacity: 0, y: -6, scale: 0.97 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: -6, scale: 0.97 }}
            transition={{ duration: 0.18, ease: EASE }}
            style={{ transformOrigin: "top right" }}
            className="absolute right-0 top-full z-20 mt-2 w-64 overflow-hidden rounded-2xl bg-surface p-1.5 text-left shadow-[0_20px_45px_-15px_rgba(28,26,22,0.35)] ring-1 ring-line"
          >
            <button
              type="button"
              onClick={onCopiarTexto}
              className="flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left transition-colors hover:bg-gold/10"
            >
              <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-gold-pale text-gold-deep">
                {copiado ? <Check className="h-4 w-4" weight="bold" /> : <ClipboardText className="h-4 w-4" weight="bold" />}
              </span>
              <span>
                <span className="block text-sm font-medium text-ink">Copiar texto para compartir</span>
                <span className="block text-xs text-ink-soft">Sirve para Instagram, o pégalo donde quieras</span>
              </span>
            </button>
            <button
              type="button"
              onClick={onCompartirWhatsapp}
              className="flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-left transition-colors hover:bg-gold/10"
            >
              <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-gold-pale text-gold-deep">
                <WhatsappLogo className="h-4 w-4" weight="bold" />
              </span>
              <span className="block text-sm font-medium text-ink">Compartir por WhatsApp</span>
            </button>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
