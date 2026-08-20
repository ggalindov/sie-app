"use client";

import { useEffect } from "react";
import { registrarVisita } from "@/lib/api";

// Identificador anónimo generado en el navegador (no es una cookie de terceros, no
// rastrea entre sitios, no lleva ningún dato personal) para que el contador de
// "visitantes este mes" del panel administrativo aproxime personas distintas en vez de
// solo cargas de página. localStorage lo hace persistir entre sesiones (así no se cuenta
// dos veces al mismo visitante en el mes); sessionStorage evita reenviar la misma visita
// en cada navegación interna dentro de la misma pestaña.
const CLAVE_VISITANTE = "sie-visitante-id";
const CLAVE_SESION_ENVIADA = "sie-visita-enviada";

export function VisitorTracker() {
  useEffect(() => {
    if (sessionStorage.getItem(CLAVE_SESION_ENVIADA)) {
      return;
    }

    let visitanteId = localStorage.getItem(CLAVE_VISITANTE);
    if (!visitanteId) {
      visitanteId = crypto.randomUUID();
      localStorage.setItem(CLAVE_VISITANTE, visitanteId);
    }

    registrarVisita(visitanteId)
      .then(() => sessionStorage.setItem(CLAVE_SESION_ENVIADA, "1"))
      .catch(() => {
        // Best effort: si falla (backend caído, red, etc.) no se marca como enviada, así
        // se reintenta en la siguiente carga de página sin afectar al visitante.
      });
  }, []);

  return null;
}
