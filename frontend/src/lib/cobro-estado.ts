// Clasificación compartida del estado real de un cobro (ver /admin/cobros y la ficha 360 del
// CRM): antes cada pantalla tenía su propia lógica binaria (pagoEsteMes ? "aprobado" : "pendiente"),
// que mezclaba "el cliente nunca respondió" con "el cliente respondió explícitamente que no" bajo
// la misma etiqueta. Esta es la única fuente de verdad para esa clasificación en el frontend, para
// que ambas pantallas siempre coincidan.
export type EstadoCobro = "PENDIENTE" | "NO_PAGO" | "APROBADO" | "SIN_COSTO";

export function tieneCostoCobro(honorarios: string | null | undefined): boolean {
  if (!honorarios) return false;
  return /[1-9]/.test(honorarios);
}

export function estadoDeCobro(cobro: {
  honorarios: string | null | undefined;
  pagoEsteMes: boolean | null;
  respondioMensaje: string | null;
}): EstadoCobro {
  if (!tieneCostoCobro(cobro.honorarios)) return "SIN_COSTO";
  if (cobro.pagoEsteMes) return "APROBADO";
  if (cobro.respondioMensaje?.trim().toUpperCase() === "NO") return "NO_PAGO";
  return "PENDIENTE";
}
