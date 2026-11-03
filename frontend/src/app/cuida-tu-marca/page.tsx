import type { Metadata } from "next";
import { Lexend, Poppins } from "next/font/google";
import { CuidaMarcaLanding } from "@/components/cuida-marca-landing";

const lexend = Lexend({
  subsets: ["latin"],
  variable: "--font-cuida-display",
});

const poppins = Poppins({
  subsets: ["latin"],
  weight: ["400", "500", "600"],
  variable: "--font-cuida-sans",
});

export const metadata: Metadata = {
  title: "Protege tu marca",
  description:
    "Registra tu marca legalmente ante la Superintendencia de Industria y Comercio, sin complicaciones y con asesoría experta. La consulta es gratis.",
};

export default function CuidaTuMarcaPage() {
  return (
    <div className={`${lexend.variable} ${poppins.variable} min-h-full flex flex-col flex-1`}>
      <CuidaMarcaLanding />
    </div>
  );
}
