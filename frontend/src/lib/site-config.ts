export const siteConfig = {
  nombre: "SIE Jurídicos",
  telefono: "+57 324 3668845",
  telefonoHref: "tel:+573243668845",
  correo: "gerencia@siejuridicos.com",
  ciudad: "Bogotá D.C., Colombia",
  whatsapp:
    "https://wa.me/573243668845?text=%C2%A1Hola%2C%20equipo%20SIE%20Jur%C3%ADdicos!%20Quiero%20m%C3%A1s%20informaci%C3%B3n%20sobre%20sus%20servicios%20legales.",
  redes: {
    facebook: "https://www.facebook.com/siejuridicos/",
    instagram: "https://www.instagram.com/siejuridicos/",
    linkedin: "https://www.linkedin.com/in/sie-juridicos-703a3b313/",
  },
  // un solo intent de CTA en todo el sitio: "agendar"
  ctaPrincipal: "Agendar asesoría",
} as const;

export const navLinks = [
  { href: "#quienes-somos", label: "La firma" },
  { href: "#areas", label: "Áreas de práctica" },
  { href: "#equipo", label: "Equipo" },
  { href: "/blog", label: "Blog y Noticias" },
  { href: "#contacto", label: "Contacto" },
] as const;
