import { Hero } from "@/components/hero";
import { SectionVideo } from "@/components/section-video";
import { Criterio } from "@/components/criterio";
import { QuienesSomos } from "@/components/quienes-somos";
import { AreasPractica } from "@/components/areas-practica";
import { TalentoHumano } from "@/components/talento-humano";
import { Equipo } from "@/components/equipo";
import { Testimonios } from "@/components/testimonios";
import { TrustedBy } from "@/components/trusted-by";
import { BlogTeaser } from "@/components/blog-teaser";
import { Newsletter } from "@/components/newsletter";
import { Contacto } from "@/components/contacto";
import { AgendarAsesoria } from "@/components/agendar-asesoria";

export default function Home() {
  return (
    <main className="flex-1">
      <Hero
        media={
          <SectionVideo
            src="videos/hero.mp4"
            poster="/marca/hero-building.jpg"
            posterAlt="Oficina de SIE Jurídicos"
            className="h-full w-full object-cover"
          />
        }
      />
      <Criterio />
      <QuienesSomos />
      <AreasPractica />
      <TalentoHumano
        media={
          <SectionVideo
            src="videos/oficina.mp4"
            poster="/marca/hero-building.jpg"
            posterAlt="Edificio corporativo de SIE Jurídicos"
            className="h-full w-full object-cover"
          />
        }
      />
      <Equipo />
      <Testimonios />
      <TrustedBy />
      <BlogTeaser />
      <Newsletter />
      <Contacto />
      <AgendarAsesoria />
    </main>
  );
}
