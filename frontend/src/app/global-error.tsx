"use client";

import { useEffect } from "react";

// Red de seguridad final: si el error ocurre en el layout raíz mismo (no en
// una página por debajo), error.tsx normal no lo captura — Next.js exige que
// global-error.tsx reemplace TODO el documento (su propio <html>/<body>),
// porque en ese punto ya no hay layout raíz vivo para envolver nada. No usa
// ningún componente del sitio (SiteChrome, fuentes, etc.) a propósito: si el
// layout mismo fue el que falló, apoyarse en piezas del layout podría volver
// a fallar. Estilos inline, sin depender de globals.css ni de las fuentes
// cargadas por next/font.
export default function GlobalError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error("Error crítico en el layout raíz:", error);
  }, [error]);

  return (
    <html lang="es">
      <body
        style={{
          margin: 0,
          minHeight: "100dvh",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          padding: "24px",
          fontFamily: "system-ui, -apple-system, sans-serif",
          background: "#14130f",
          color: "#f3ede0",
        }}
      >
        <div style={{ maxWidth: 420, textAlign: "center" }}>
          <p style={{ fontSize: 14, letterSpacing: "0.08em", textTransform: "uppercase", color: "#d9a925" }}>
            SIE Jurídicos
          </p>
          <h1 style={{ marginTop: 16, fontSize: 24, lineHeight: 1.3 }}>
            El sitio tuvo un problema inesperado.
          </h1>
          <p style={{ marginTop: 12, fontSize: 15, lineHeight: 1.6, color: "rgba(243,237,224,0.7)" }}>
            Ya quedó registrado. Intenta recargar la página en unos momentos.
          </p>
          <button
            type="button"
            onClick={() => reset()}
            style={{
              marginTop: 24,
              padding: "14px 28px",
              borderRadius: 999,
              border: "none",
              background: "#d9a925",
              color: "#14130f",
              fontSize: 15,
              fontWeight: 500,
              cursor: "pointer",
            }}
          >
            Recargar
          </button>
        </div>
      </body>
    </html>
  );
}
