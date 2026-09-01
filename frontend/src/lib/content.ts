export type AreaPractica = {
  slug: string;
  nombre: string;
  resumen: string;
  descripcion: string[];
  whatsappMensaje: string;
};

export const areasPractica: AreaPractica[] = [
  {
    slug: "laboral",
    nombre: "Derecho Laboral",
    resumen:
      "Defendemos los derechos de trabajadores y empleadores en cada etapa de la relación laboral.",
    descripcion: [
      "Acompañamos a trabajadores y empleadores en contratación, liquidaciones, despidos y procesos ante el Ministerio del Trabajo.",
      "Revisamos contratos, reglamentos internos y políticas de la empresa para prevenir contingencias antes de que ocurran.",
    ],
    whatsappMensaje:
      "Hola, tengo una consulta sobre Derecho Laboral y quisiera hablar con un abogado.",
  },
  {
    slug: "familia",
    nombre: "Derecho de Familia",
    resumen:
      "Acompañamiento legal integral con sensibilidad, confidencialidad y compromiso.",
    descripcion: [
      "Divorcios, custodia, alimentos, sucesiones y liquidación de sociedad conyugal, siempre con un enfoque humano.",
      "Sabemos que estos procesos involucran emociones, por eso combinamos el conocimiento jurídico con acompañamiento cercano.",
    ],
    whatsappMensaje:
      "Hola, necesito asesoría en un tema de Derecho de Familia.",
  },
  {
    slug: "civil",
    nombre: "Derecho Civil",
    resumen:
      "Una de las ramas más amplias del derecho: relaciones entre personas, bienes y obligaciones.",
    descripcion: [
      "Contratos civiles, responsabilidad civil, propiedad, arrendamientos y procesos declarativos.",
      "Representamos tus intereses tanto en la negociación como en litigio, buscando siempre la solución más eficiente.",
    ],
    whatsappMensaje: "Hola, tengo una consulta de Derecho Civil.",
  },
  {
    slug: "mercantil",
    nombre: "Derecho Mercantil y Propiedad Intelectual",
    resumen:
      "Regulamos las relaciones comerciales y protegemos las creaciones intelectuales de tu empresa.",
    descripcion: [
      "Constitución de sociedades, contratos comerciales, fusiones y adquisiciones, y gobierno corporativo.",
      "En Propiedad Intelectual protegemos marcas, patentes, derechos de autor y diseños industriales.",
    ],
    whatsappMensaje:
      "Hola, quiero información sobre Derecho Mercantil o registro de marca.",
  },
  {
    slug: "administrativo",
    nombre: "Derecho Administrativo y Contratación Estatal",
    resumen:
      "Asesoría en la relación entre el Estado y los ciudadanos, y en procesos de contratación pública.",
    descripcion: [
      "Representamos a nuestros clientes ante entidades públicas y en procesos de contratación estatal.",
      "Litigios administrativos, nulidad de actos, y acompañamiento en licitaciones y convenios con el Estado.",
    ],
    whatsappMensaje:
      "Hola, tengo una consulta sobre Derecho Administrativo o Contratación Estatal.",
  },
  {
    slug: "constitucional",
    nombre: "Derecho Constitucional y Derecho Internacional",
    resumen:
      "Protegemos los derechos fundamentales y representamos a nuestros clientes en contextos internacionales.",
    descripcion: [
      "Acciones de tutela, derechos de petición y defensa frente a vulneraciones por parte del Estado o particulares.",
      "Acompañamos personas y empresas en trámites y litigios que trascienden fronteras.",
    ],
    whatsappMensaje:
      "Hola, necesito orientación en un tema de Derecho Constitucional o Internacional.",
  },
];

export type MiembroEquipo = {
  nombre: string;
  cargo: string;
  foto: string;
  bio?: string;
  // encuadre por foto: las fotos reales del equipo vienen con zoom y
  // encuadre distintos entre sí (algunas muy cerca de rostro, otras con
  // mucho fondo, una descentrada). No hay herramienta de recorte/edición de
  // imagen disponible en este entorno, así que se corrige por CSS: posicion
  // es object-position, zoom es un scale() aplicado sobre la imagen para que
  // todos los rostros lean a un tamaño y encuadre parecido en la grilla.
  posicion?: string;
  zoom?: number;
};

// Bios de Tatiana y Jorge Mario tomadas literalmente de sus páginas de perfil
// reales en siejuridicos.com (contenido publicado por la firma, no inventado).
// El resto del equipo no tiene bio publicada todavía: se muestra solo
// nombre y cargo hasta que la firma provea el texto real de cada persona.
export const equipo: MiembroEquipo[] = [
  {
    nombre: "Tatiana Marcela Bustos Moreno",
    cargo: "Socia Fundadora",
    foto: "/equipo/tatiana-bustos.jpg",
    bio: "Especialista en Derecho Administrativo y Probatorio, Magíster en Administración Estratégica de Recursos Humanos. Más de 20 años de experiencia en el sector público y privado, con cargos como Directora de Conciliación en la Personería de Bogotá y asesora jurídica en la Contraloría General de la República.",
    posicion: "50% 32%",
  },
  {
    nombre: "Jorge Mario Cifuentes Lara",
    cargo: "Abogado Senior",
    foto: "/equipo/jorge-mario.png",
    bio: "Especialista en Derecho Administrativo, Conciliador en Derecho. Experto en Derecho Laboral, Seguridad Social, Tributario, Contractual y procesos de insolvencia, reestructuración y liquidación, con más de 20 años de experiencia en el sector público y privado.",
    posicion: "50% 30%",
    zoom: 0.94,
  },
  {
    nombre: "Oscar Fernando Rincón",
    cargo: "Abogado Senior",
    foto: "/equipo/oscar-rincon.jpg",
    posicion: "50% 28%",
    zoom: 0.94,
  },
  {
    nombre: "Natalia Cárdenas Triviño",
    cargo: "Abogada",
    foto: "/equipo/natalia-cardenas.jpg",
    posicion: "62% 30%",
    zoom: 1.18,
  },
  {
    nombre: "Karen Yalena Cantillo Martínez",
    cargo: "Abogada",
    foto: "/equipo/karen-cantillo.jpg",
    posicion: "50% 30%",
  },
  {
    nombre: "Antonio Farfán Barrero",
    cargo: "Abogado Senior",
    foto: "/equipo/antonio-farfan.jpg",
    posicion: "48% 22%",
  },
  {
    nombre: "Valentina Castañeda",
    cargo: "Abogada",
    foto: "/equipo/valentina-castaneda.jpg",
    posicion: "50% 22%",
    zoom: 1.05,
  },
  {
    nombre: "Kory Violeta Mendoza Sarabia",
    cargo: "Líder de Gestión",
    foto: "/equipo/kory-mendoza.png",
    posicion: "50% 28%",
    zoom: 1.1,
  },
  {
    nombre: "Iris Edelmira Pinzón",
    cargo: "Gestora Jurídica",
    foto: "/equipo/iris-pinzon.png",
    posicion: "50% 26%",
    zoom: 1.12,
  },
  {
    nombre: "Alejandra Villate",
    cargo: "Practicante",
    foto: "/equipo/alejandra-villate.jpg",
    posicion: "48% 28%",
    zoom: 1.02,
  },
  {
    nombre: "Juan David Galindo",
    cargo: "Soporte Técnico",
    foto: "/equipo/juan-david-galindo.jpg",
    bio: "Ingeniero de Sistemas de la Universidad El Bosque.",
    posicion: "50% 20%",
  },
];

export type Testimonio = {
  cita: string;
  nombre: string;
  cargo: string;
};

export const testimonios: Testimonio[] = [
  {
    cita: "Nuestra empresa, Samval Ltda, ha tenido el privilegio de trabajar con esta firma durante 4 años y hemos quedado completamente satisfechos con su desempeño.",
    nombre: "Julián Mondragón",
    cargo: "Gerente General, Samval Ltda",
  },
  {
    cita: "Desde hace 12 años puedo estar tranquilo desde que contratamos a SIE Jurídicos. Tenemos cobertura en la contratación con clientes, contratistas y talento humano, sin ningún inconveniente jurídico.",
    nombre: "Leonardo García",
    cargo: "Gerente General",
  },
];

export type EmpresaConfianza = { src: string; alt: string };

export const empresasConfianza: EmpresaConfianza[] = [
  { src: "/confianza/symaa.png", alt: "Symaa Ingeniería" },
  { src: "/confianza/connect.png", alt: "Connect Americas" },
  { src: "/confianza/dy.png", alt: "Dypsion International" },
  { src: "/confianza/ck.png", alt: "CK" },
  { src: "/confianza/tec.png", alt: "Tecsai Ingeniería" },
  { src: "/confianza/work.png", alt: "WorkeR Company" },
  { src: "/confianza/sm.png", alt: "Samval" },
];
