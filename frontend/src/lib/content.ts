export type AreaPractica = {
  slug: string;
  nombre: string;
  resumen: string;
  descripcion: string[];
  foto: string;
  destacado: string;
  casosReferencia: string[];
  whatsappMensaje: string;
};

export const areasPractica: AreaPractica[] = [
  {
    slug: "laboral",
    nombre: "Derecho Laboral",
    destacado: "Defensa estratégica y gestión laboral",
    foto: "/areas/area-laboral-trabajadores.jpg",
    casosReferencia: [
      "Despidos sin justa causa e indemnizaciones",
      "Fueros de estabilidad laboral reforzada",
      "Auditorías de nómina y contratación ejecutiva",
      "Conciliaciones y querellas ante MinTrabajo",
    ],
    resumen:
      "Defendemos los derechos de trabajadores y empleadores en cada etapa de la relación laboral y corporativa.",
    descripcion: [
      "Acompañamos a trabajadores y empresas en contratación, liquidaciones indemnizatorias, fueros de salud, maternidad y procesos ante el Ministerio del Trabajo.",
      "Revisamos contratos, reglamentos internos y políticas corporativas para prevenir contingencias y litigios de alto costo.",
    ],
    whatsappMensaje:
      "Hola, tengo una consulta sobre Derecho Laboral y quisiera hablar con un abogado.",
  },
  {
    slug: "familia",
    nombre: "Derecho de Familia",
    destacado: "Resolución humana y protección patrimonial",
    foto: "/areas/area-familia-hogar.jpg",
    casosReferencia: [
      "Divorcios contenciosos y de mutuo acuerdo",
      "Sucesiones y liquidación de herencias complejas",
      "Custodia, visitas y fijación de cuota de alimentos",
      "Capitulaciones y liquidación de sociedad conyugal",
    ],
    resumen:
      "Acompañamiento legal integral con máxima sensibilidad, confidencialidad y rigor patrimonial.",
    descripcion: [
      "Divorcios, custodia, alimentos, sucesiones y liquidación de sociedad conyugal, siempre con un enfoque humano y constructivo.",
      "Entendemos que estos procesos involucran personas y emociones familiares; combinamos el más estricto rigor jurídico con una atención cercana y discreta.",
    ],
    whatsappMensaje:
      "Hola, necesito asesoría en un tema de Derecho de Familia.",
  },
  {
    slug: "civil",
    nombre: "Derecho Civil",
    destacado: "Seguridad patrimonial, bienes y contratos",
    foto: "/areas/area-civil-inmuebles.jpg",
    casosReferencia: [
      "Responsabilidad civil contractual y médica",
      "Estudio de títulos y litigios inmobiliarios",
      "Procesos ejecutivos y recuperación de cartera",
      "Incumplimiento de contratos y restitución",
    ],
    resumen:
      "Una de las ramas más amplias del derecho: blindaje de relaciones patrimoniales, bienes y obligaciones.",
    descripcion: [
      "Contratos civiles, compraventas, responsabilidad civil extracontractual, propiedad, arrendamientos y procesos declarativos.",
      "Representamos tus intereses tanto en la mesa de negociación como en litigio ante juzgados, buscando siempre la máxima eficiencia y protección de tu patrimonio.",
    ],
    whatsappMensaje: "Hola, tengo una consulta de Derecho Civil.",
  },
  {
    slug: "mercantil",
    nombre: "Derecho Mercantil y Propiedad Intelectual",
    destacado: "Estructuración corporativa y marcas",
    foto: "/areas/area-mercantil-acuerdos.jpg",
    casosReferencia: [
      "Registro y defensa de marcas y patentes ante la SIC",
      "Constitución de sociedades y acuerdos de socios",
      "Fusiones, adquisiciones (M&A) y reestructuración",
      "Contratos comerciales, franquicias y compliance",
    ],
    resumen:
      "Regulamos las relaciones comerciales y protegemos las creaciones e intangibles estratégicos de tu empresa.",
    descripcion: [
      "Constitución de sociedades comerciales, acuerdos de socios, gobierno corporativo, fusiones y adquisiciones.",
      "En Propiedad Intelectual protegemos marcas, patentes, derechos de autor, secretos empresariales y diseños industriales ante la SIC y tribunales.",
    ],
    whatsappMensaje:
      "Hola, quiero información sobre Derecho Mercantil o registro de marca.",
  },
  {
    slug: "administrativo",
    nombre: "Derecho Administrativo y Contratación Estatal",
    destacado: "Litigio contencioso y licitaciones públicas",
    foto: "/areas/administrativo-palacio-justicia.jpg",
    casosReferencia: [
      "Acompañamiento en licitaciones y pliegos públicos",
      "Demandas de nulidad y restablecimiento del derecho",
      "Reparación directa por daño antijurídico del Estado",
      "Procesos disciplinarios y sancionatorios estatales",
    ],
    resumen:
      "Asesoría especializada en la relación público-privada, licitaciones estatales y defensa contenciosa.",
    descripcion: [
      "Representamos a empresas y ciudadanos ante entidades del orden nacional y territorial, y en procesos de contratación pública bajo Ley 80 y SECOP.",
      "Litigios contencioso-administrativos, controversias contractuales, nulidad de actos y acompañamiento técnico en licitaciones y convenios.",
    ],
    whatsappMensaje:
      "Hola, tengo una consulta sobre Derecho Administrativo o Contratación Estatal.",
  },
  {
    slug: "constitucional",
    nombre: "Derecho Constitucional y Derecho Internacional",
    destacado: "Protección de libertades y litigio de alto impacto",
    foto: "/areas/constitucional-balanza-justicia.jpg",
    casosReferencia: [
      "Acciones de tutela en salud, debido proceso y pensión",
      "Acciones populares y de cumplimiento",
      "Derechos de petición complejos e incidentes de desacato",
      "Asesoría transfronteriza y litigio internacional",
    ],
    resumen:
      "Protegemos los derechos fundamentales y representamos a nuestros clientes en instancias constitucionales e internacionales.",
    descripcion: [
      "Acciones de tutela de alta complejidad, derechos de petición estratégicos y defensa frente a vulneraciones arbitrarias de entidades públicas o privadas.",
      "Acompañamos a personas y organizaciones en trámites y litigios de alto impacto que trascienden fronteras y requieren jurisprudencia constitucional especializada.",
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
    nombre: "Antonio Farfán Barrero",
    cargo: "Abogado Socio",
    foto: "/equipo/antonio-farfan.jpg",
    posicion: "48% 22%",
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
    nombre: "Valentina Castañeda",
    cargo: "Abogada",
    foto: "/equipo/valentina-castaneda.jpg",
    posicion: "50% 22%",
    zoom: 1.05,
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
    cargo: "Líder de Gestión",
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
  { src: "/confianza/offigrand.png", alt: "OffiGrand" },
  { src: "/confianza/espacios-moviles.png", alt: "Espacios Móviles S.A.S." },
  { src: "/confianza/danko.png", alt: "DANKO Muebles & Diseño S.A.S." },
  { src: "/confianza/villapan.png", alt: "Villapan" },
  { src: "/confianza/reyco.png", alt: "REYCO Inversiones SAS" },
  { src: "/confianza/ochoa.png", alt: "Ochoa y Compañía" },
  { src: "/confianza/rumbos.png", alt: "Transportes Especiales Rumbos SAS" },
  { src: "/confianza/iedcc.png", alt: "Iglesia Evangélica Discípulos de Cristo" },
  { src: "/confianza/el-pomar.png", alt: "Comercializadora El Pomar S.A.S." },
  { src: "/confianza/enerpetrol.png", alt: "Enerpetrol S.A.S." },
  { src: "/confianza/quiminsa.png", alt: "Quiminsa S.A.S." },
  { src: "/confianza/pst-colombia.png", alt: "PST de Colombia" },
  { src: "/confianza/funcea.png", alt: "FUNCEA Colombia" },
  { src: "/confianza/diaz-store.png", alt: "Díaz Store" },
];
