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
            src="videos/abogado.mp4"
            poster="/marca/hero-building.jpg"
            posterAlt="Abogado de SIE Jurídicos"
            className="h-full w-full object-cover"
          />
        }
      />
      <Criterio
        media={
          <SectionVideo
            src="videos/balanza.mp4"
            poster="/fondos/dmitrij-paskevic-YjVa-F9P9kk-unsplash.jpg"
            posterAlt="Balanza de la justicia"
            className="h-full w-full object-cover"
          />
        }
      />
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
      <AgendarAsesoria
        media={
          <SectionVideo
            src="videos/documentos.mp4"
            poster="/marca/hero-building.jpg"
            posterAlt="Documentos legales"
            className="h-full w-full object-cover"
          />
        }
      />
    </main>
  );
}
