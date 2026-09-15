import { Hero } from "@/components/hero";
import { SectionVideo } from "@/components/section-video";
import { Criterio } from "@/components/criterio";
import { Equipo } from "@/components/equipo";
import { AreasPractica } from "@/components/areas-practica";
import { Testimonios } from "@/components/testimonios";
import { QuienesSomos } from "@/components/quienes-somos";
import { TrustedBy } from "@/components/trusted-by";
import { BlogTeaser } from "@/components/blog-teaser";
import { Contacto } from "@/components/contacto";
import { AgendarAsesoria } from "@/components/agendar-asesoria";

export default function Home() {
  return (
    <main className="flex-1">
      {/* 1. Veinte años de experiencia legal, a tu lado */}
      <Hero
        media={
          <SectionVideo
            src="videos/abogado.mp4"
            poster="/marca/hero-building.jpg"
            posterAlt="Abogado de SIE Jurídicos"
            className="h-full w-full object-cover"
            priority
          />
        }
      />

      {/* 2. Cada caso se resuelve con el mismo criterio: veinte años de experiencia */}
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

      {/* 3. Nuestro equipo */}
      <Equipo />

      {/* 4. Áreas de práctica */}
      <AreasPractica />

      {/* 5. Lo que dicen nuestros clientes */}
      <Testimonios
        media={
          <SectionVideo
            src="videos/abogada.mp4"
            poster="/marca/hero-building.jpg"
            posterAlt="Abogada de SIE Jurídicos"
            className="h-full w-full object-cover"
          />
        }
      />

      {/* 6. Más que abogados: aliados en cada decisión importante */}
      <QuienesSomos
        media={
          <SectionVideo
            src="videos/firma.mp4"
            poster="/marca/hero-building.jpg"
            posterAlt="Firma de documentos legales"
            className="h-full w-full object-cover"
          />
        }
      />

      {/* 7. La confianza de empresas que ya nos eligieron */}
      <TrustedBy />

      {/* 8. Blog y análisis legal con nuevo layout editorial */}
      <BlogTeaser />

      {/* 9. Contacto */}
      <Contacto />

      {/* 10. Agendar asesoría */}
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
