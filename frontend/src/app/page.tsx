import { Hero } from "@/components/hero";
import { Criterio } from "@/components/criterio";
import { QuienesSomos } from "@/components/quienes-somos";
import { AreasPractica } from "@/components/areas-practica";
import { TalentoHumano } from "@/components/talento-humano";
import { Equipo } from "@/components/equipo";
import { Testimonios } from "@/components/testimonios";
import { TrustedBy } from "@/components/trusted-by";
import { BlogTeaser } from "@/components/blog-teaser";
import { Contacto } from "@/components/contacto";

export default function Home() {
  return (
    <main className="flex-1">
      <Hero />
      <Criterio />
      <QuienesSomos />
      <AreasPractica />
      <TalentoHumano />
      <Equipo />
      <Testimonios />
      <TrustedBy />
      <BlogTeaser />
      <Contacto />
    </main>
  );
}
