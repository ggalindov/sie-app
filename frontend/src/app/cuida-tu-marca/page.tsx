import type { Metadata } from "next";
import { CuidaMarcaLanding } from "@/components/cuida-marca-landing";

export const metadata: Metadata = {
  title: "Protege tu marca",
  description:
    "Registra tu marca legalmente ante la Superintendencia de Industria y Comercio, sin complicaciones y con asesoría experta. La consulta es gratis.",
};

export default function CuidaTuMarcaPage() {
  return <CuidaMarcaLanding />;
}
