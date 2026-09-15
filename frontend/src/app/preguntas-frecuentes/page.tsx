import type { Metadata } from "next";
import { getFaq } from "@/lib/api";
import { PreguntasFrecuentesCliente } from "./preguntas-frecuentes-cliente";

export const metadata: Metadata = {
  title: "Preguntas frecuentes",
  description:
    "Respuestas y criterio legal claro sobre procesos laborales, de familia, contratos, tarifas y tiempos judiciales en Colombia.",
};

export default async function PreguntasFrecuentesPage() {
  const preguntasBackend = await getFaq().catch(() => []);

  return <PreguntasFrecuentesCliente preguntasBackend={preguntasBackend} />;
}

