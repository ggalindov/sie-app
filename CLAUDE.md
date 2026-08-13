# SIE Jurídicos — Contexto del proyecto

Documento de traspaso generado tras una sesión muy larga de construcción de backend,
frontend y un ciclo completo de rediseño visual/animación. Léelo completo antes de tocar
código: varias decisiones aquí no son obvias desde el código solo, y varios de los
"gotchas" listados costaron bastante descubrirlos (algunos llevaron horas).

## 1. Qué es esto

Sistema para SIE Jurídicos, firma de abogados en Bogotá (sitio real: siejuridicos.com,
actualmente en WordPress, en proceso de reemplazo). Módulos: sitio institucional, blog
jurídico, testimonios públicos moderados, panel de administración de leads, formulario de
contacto, chatbot con IA, autenticación de usuarios internos, correo transaccional, citas.

**No hay registro público de usuarios.** Los únicos usuarios del sistema son internos de
la firma (`ADMIN_GENERAL` y `ABOGADO`), creados manualmente por el admin general. Los
visitantes solo dejan datos como leads (formulario, chatbot o testimonio), nunca inician
sesión.

## 2. Stack

- **Backend**: Java 21, Spring Boot **4.1.0** (no 3.x — el proyecto ya venía inicializado
  así y se decidió mantenerlo), Maven, PostgreSQL 16, Flyway, Spring Security + JWT,
  Spring AI (Claude Haiku) para el chatbot, Spring Mail.
- **Frontend**: Next.js **16.3.0** (App Router, Turbopack), React 19.2, TypeScript,
  Tailwind CSS v4, **Motion** (antes Framer Motion) para toda la animación de UI, **GSAP +
  ScrollTrigger** para coreografía ligada al scroll (indicador de progreso, continuidad de
  color entre secciones), `@phosphor-icons/react`, `@base-ui/react`, Sonner.
- **Infra local**: Docker Compose (Postgres + pgAdmin), `run.bat` para arrancar el backend
  desde cmd en Windows.

## 3. Estado actual (qué está hecho y probado end-to-end, qué falta)

✅ Backend completo: auth/JWT, usuarios internos, solicitudes (leads), blog, chatbot,
correo transaccional, citas, marketing, **testimonios públicos moderados** (nuevo, ver
sección 4.4).
✅ Frontend público completo y con un ciclo grande de rediseño visual/animación ya hecho:
modo claro/oscuro con toggle, fondos siempre en degradado animado (nunca color plano),
GSAP para scroll, botones magnéticos, contador animado en cifras, carrusel de empresas de
confianza más grande y legible, sección de equipo con fotos circulares enmascaradas.
✅ Todo probado real contra el backend corriendo (no solo compilado): formulario crea
solicitudes reales, blog muestra artículos reales, testimonios se envían y se pueden
moderar, CORS verificado, `npm run build` de producción sin errores (20 rutas) y
`mvnw compile` sin errores.
✅ **Panel de administración (frontend) completo y probado**, incluyendo la nueva página
`/admin/testimonios` para aprobar/rechazar. Rutas bajo `/admin/**`, login con JWT,
dashboard con estadísticas, gestión de solicitudes (cambio de estado + agendar cita), CRUD
completo de artículos con editor rich-text (Tiptap), gestión de usuarios internos (solo
`ADMIN_GENERAL`), listado de suscriptores de marketing, cambio de contraseña propia.
Probado end-to-end con ambos roles (`ADMIN_GENERAL` y `ABOGADO`).
❌ Sin credenciales reales de SMTP ni de Anthropic todavía (ver sección 7).
❌ Los artículos del blog son de **prueba**, creados para validar la integración (ver
sección 8). Hay que reemplazarlos por contenido real.
❌ La tabla `testimonios_publicos` está vacía en producción hasta que el backend real
corra y alguien envíe un testimonio (o un admin cargue algunos manualmente).

**Cosas que se probaron y luego se revirtieron a propósito** (para que no las reintentes
sin que el usuario lo pida): hubo una pieza 3D de un martillo de juez hecha con Three.js /
React Three Fiber (`gavel-3d.tsx`, `signature-moment.tsx`) como "momento de firma" de la
home. El usuario pidió explícitamente quitarla ("pierde la elegancia del sitio"). Se
eliminaron los componentes y se desinstalaron `three`, `@react-three/fiber` y
`@react-three/drei` del `package.json`. No hay ningún elemento 3D en el sitio actualmente.

## 4. Backend: estructura y decisiones

Paquete base: `sie.siejuridicos`, organizado por módulo (`usuario`, `solicitud`,
`articulo`, `categoria`, `chatbot`, `correo`, `marketing`, `testimonio`, `security`,
`common.exception`).

### 4.1 Modelo de datos (Flyway, `src/main/resources/db/migration/`)

`V1`-`V10`: tablas base (`usuarios_internos`, `categorias`, `articulos`, `solicitudes`,
`conversaciones_chatbot`) + las 4 funciones PL/pgSQL pedidas + seed de 7 categorías.
`V11`: agrega `fecha_cita` y `recordatorio_enviado` a `solicitudes`.
`V12`: crea `suscriptores_marketing`.
`V13`: crea `testimonios_publicos` (ver sección 4.4).

**Funciones PL/pgSQL** (lógica de datos en la BD, negocio en Java):
- `fn_crear_solicitud`: valida duplicado exacto (correo+mensaje) en 24h.
- `fn_actualizar_estado_solicitud`: valida transición de estado (CERRADO es terminal,
  no puede volver a NUEVO ni CONTACTADO).
- `fn_contar_conversaciones_mes_actual`: para el tope de 500 chats/mes.
- `fn_publicar_articulo`: valida integridad (título/contenido/categoría) antes de publicar.

Cada función lanza `RAISE EXCEPTION ... USING ERRCODE = 'SIE01'..'SIE05'`, que Java
traduce a excepciones de negocio en `ErroresBaseDatos.traducir()`. Los testimonios NO usan
una función PL/pgSQL propia, la validación de estado vive en `TestimonioService` (Java
puro), fue suficiente para la complejidad del caso.

### 4.2 Endpoints

| Método | Ruta | Acceso |
|---|---|---|
| POST | `/api/auth/login` | público |
| PATCH | `/api/auth/password` | autenticado (cualquier rol) |
| POST | `/api/admin/usuarios` | solo `ADMIN_GENERAL` (crea siempre rol `ABOGADO`) |
| GET | `/api/admin/usuarios` | solo `ADMIN_GENERAL` |
| PATCH | `/api/admin/usuarios/{id}/activo` | solo `ADMIN_GENERAL` |
| POST | `/api/solicitudes` | público |
| GET/PATCH | `/api/admin/solicitudes/**` | `ADMIN_GENERAL` o `ABOGADO` |
| PATCH | `/api/admin/solicitudes/{id}/cita` | `ADMIN_GENERAL` o `ABOGADO` |
| GET | `/api/categorias` | público |
| GET | `/api/articulos`, `/api/articulos/{slug}` | público |
| GET | `/api/admin/articulos` | `ADMIN_GENERAL` o `ABOGADO` (lista todos, incluye borradores) |
| GET | `/api/admin/articulos/{id}` | `ADMIN_GENERAL` o `ABOGADO` (para el editor) |
| POST/PUT/DELETE | `/api/admin/articulos/**` | `ADMIN_GENERAL` o `ABOGADO` |
| POST | `/api/chatbot/mensaje` | público |
| GET | `/api/admin/marketing/suscriptores` | `ADMIN_GENERAL` o `ABOGADO` |
| POST | `/api/testimonios` | público (queda en estado `PENDIENTE`) |
| GET | `/api/testimonios` | público (solo devuelve `APROBADO`) |
| GET | `/api/admin/testimonios` | `ADMIN_GENERAL` o `ABOGADO` (todos, cualquier estado) |
| PATCH | `/api/admin/testimonios/{id}` | `ADMIN_GENERAL` o `ABOGADO` (aprobar/rechazar) |

Colección Postman completa en `postman/SIE-Juridicos.postman_collection.json` (no
actualizada todavía con los endpoints de testimonios, hazlo si vas a probar con Postman).

### 4.3 Funcionalidad de correo, citas y marketing

- **Al crear solicitud**: correo de confirmación+promoción al remitente, correo de
  notificación al admin (`siejuridicos@gmail.com`), y si `aceptaMarketing=true`, se guarda
  en `suscriptores_marketing` (checkbox **separado** del de tratamiento de datos, por
  tema de Habeas Data / Ley 1581).
- **Citas**: el abogado/admin las agenda manualmente sobre una solicitud existente
  (`PATCH /api/admin/solicitudes/{id}/cita`), no hay calendario público de autoagendamiento
  (decisión explícita del usuario). Dispara correo inmediato al cliente y al admin.
- **Recordatorio**: `RecordatorioCitaScheduler` corre diario (`app.recordatorios.cron`,
  default 7am) y le manda recordatorio a quien tenga cita ese día. Solo al cliente, no al
  admin (así quedó definido).
- Todo el envío de correo es `@Async` (ver `EmailService`) para no bloquear la respuesta
  HTTP ni retener conexiones de BD mientras se conecta al SMTP.

### 4.4 Testimonios públicos moderados (módulo nuevo)

Flujo: un visitante llena el modal "Deja tu testimonio" en el sitio público → se guarda
con `estado = PENDIENTE` → **no se muestra en el sitio todavía** → un usuario interno lo
revisa en `/admin/testimonios` y lo aprueba o rechaza → solo los `APROBADO` aparecen en la
sección de testimonios del home (mezclados con los dos testimonios reales que ya existían
como contenido estático en `frontend/src/lib/content.ts`).

Entidad `TestimonioPublico` (`testimonio/`): `nombre`, `empresa` (opcional), `cargo`
(opcional), `cita` (máx. 600 caracteres), `calificacion` (1-5), `correo` (nunca se expone
en la respuesta pública, solo en la vista admin), `estado` (`PENDIENTE`/`APROBADO`/
`RECHAZADO`), `fechaCreacion`, `fechaModeracion`.

**Decisión explícita de alcance**: no hay subida de fotos/archivos en el formulario de
testimonios. Se evaluó y se descartó por tiempo y por el riesgo de seguridad que implica
aceptar uploads sin restricciones (OWASP: unrestricted file upload). Si se pide en el
futuro, hay que diseñar bien el almacenamiento (¿local? ¿S3?) y la validación de tipo/
tamaño antes de implementarlo, no improvisarlo.

`TestimonioService.moderar()` rechaza explícitamente que un testimonio vuelva a
`PENDIENTE` una vez movido (`EntidadInvalidaException`), solo puede ir a `APROBADO` o
`RECHAZADO`.

### 4.5 Gotchas de Spring Boot 4.1.0 (perdí tiempo real en esto, no lo repitas)

1. **Flyway silenciosamente no hace nada.** En Spring Boot 4, la autoconfiguración de
   Flyway se movió a un módulo separado (`org.springframework.boot:spring-boot-flyway`),
   que solo se agrega si usas el starter `spring-boot-starter-flyway` — NO basta con poner
   `flyway-core` directo (eso es solo el motor de Flyway, sin la integración de Spring
   Boot). Sin el starter correcto, Flyway no aparece ni en el reporte de condiciones, no
   hay logs, no hay error: simplemente nunca corre.
2. **Jackson 2 → Jackson 3.** El `ObjectMapper` por defecto ya no es
   `com.fasterxml.jackson.databind.ObjectMapper` sino `tools.jackson.databind.ObjectMapper`
   (Jackson 3, paquete renombrado). Cualquier bean que inyecte el `ObjectMapper` clásico
   falla con "no qualifying bean".
3. **`@Lob` sobre un `String` mapea a `oid` de Postgres, no a `TEXT`.** Si tu columna es
   `TEXT` (como en las migraciones de este proyecto), NO uses `@Lob` en la entidad — solo
   `@Column`. Afectó a `Articulo.contenido`, `Solicitud.mensaje` y `notasInternas`.
4. `params`/`searchParams` en páginas dinámicas ahora son `Promise` (rompe el fetch server
   side si no se hace `await`) — esto es de Next.js 16, no de Spring, ver sección 5.

### 4.6 Seguridad

JWT (jjwt 0.12.x), stateless, claim `rol` en el token. `SecurityConfig` define las reglas
por ruta (ver tabla de endpoints). CORS configurado vía `app.cors.allowed-origins`
(default `http://localhost:3000`). No hay `AuthenticationManager`/`DaoAuthenticationProvider`
de Spring Security: el login valida credenciales manualmente en `AuthService` con
`PasswordEncoder` directo — es intencional, no un descuido.

**No hay registro público.** El primer `ADMIN_GENERAL` se crea solo si defines
`ADMIN_BOOTSTRAP_EMAIL`/`ADMIN_BOOTSTRAP_PASSWORD` (ver `AdminBootstrapRunner`).

**Sobre "medidas de seguridad" en el sitio público**: el usuario pidió en algún momento
que Contacto especificara "todos los medios de control de seguridad del aplicativo y de
la compañía". Se decidió explícitamente **no inventar** afirmaciones técnicas (cifrado,
certificaciones ISO, etc.) que no se pueden verificar como ciertas — eso sería publicar un
compromiso de cumplimiento falso, con riesgo legal real. Lo que sí se agregó fue honesto y
verificable: mención de cumplimiento de la Ley 1581 de 2012 (Habeas Data) en el bloque de
privacidad de Contacto. Si la firma tiene certificaciones o medidas reales que sí quiere
publicar, hay que pedirle el texto exacto, no redactarlo por inferencia.

## 5. Frontend: estructura y decisiones

`frontend/` es un proyecto Next.js separado (propio `package.json`, sin `.git` anidado a
propósito). Documentación de Next.js 16 bundleada en
`frontend/node_modules/next/dist/docs/` — **léela antes de asumir que sabes cómo funciona
algo de Next.js**, hay cambios reales respecto a versiones anteriores (Turbopack por
defecto, `params`/`searchParams` async, `next/legacy/image` eliminado, etc.)

### 5.1 Sistema de diseño

**Modo claro/oscuro con toggle** (no es solo dark mode, el usuario pidió ambos). El
atributo `data-theme="light"|"dark"` vive en `<html>`, lo fija un script inline
`beforeInteractive` en `layout.tsx` (lee `localStorage['sie-theme']`, si no existe usa
`prefers-color-scheme`) para no parpadear al cargar. El toggle (`theme-toggle.tsx`) solo
lo cambia después. **Esto solo aplica al sitio público** (`.site-theme` en
`site-chrome.tsx`): `/admin/**` nunca recibe esta clase y se queda siempre con la paleta
clara original a propósito (ver sección 5.6).

**Regla explícita del usuario: nunca un fondo grande de color sólido.** Todo fondo
importante es un degradado, y además debe moverse (no un degradado estático). Mecanismos:
- `.gradient-animate` (en `globals.css`): `background-size: 220% 220%` + keyframe que
  mueve `background-position`. Se aplica junto con un `backgroundImage` en línea con el
  degradado específico de cada componente (Hero, tarjetas de Áreas de práctica).
- `.ambient-field`: capa `fixed`, `z-index: -1`, detrás de todo el sitio público, con
  varios `radial-gradient` dorados/terracota/bosque/navy sobre `--page-gradient` (el
  degradado base del tema activo). Tiene su propia animación de `background-position`
  (`ambient-drift`, 26s) y además un `filter: hue-rotate(var(--scroll-hue, 0deg))` que
  gira el matiz con el scroll real (ver `scroll-ambient.tsx` en 5.4).
- `.section-seam`: en vez de un `border-t` plano entre secciones, una costura de 1px en
  degradado dorado que se desvanece en los extremos.

**Paleta** (tokens CSS en `globals.css`, mapeados a utilidades Tailwind vía `@theme
inline`, así `bg-paper`/`text-ink`/etc. funcionan igual que antes pero ahora son
sensibles al tema):
- `--color-paper`/`--color-surface`/`--color-ink`/`--color-ink-soft`/`--color-line`: se
  redefinen dentro de `html[data-theme="dark"] .site-theme { ... }`. Fuera de ese scope
  (o en `/admin`) usan los valores claros originales de `:root`.
- `--color-gold` / `--color-gold-deep` / `--color-gold-pale`: dorado real de marca
  (`#D9A925`, extraído del sitio en producción), **fijo**, no cambia con el tema.
- `--color-terracotta` / `--color-forest` / `--color-navy` (+ sus variantes `-ink` para
  texto legible encima): familia de acentos editoriales nueva, agregada para las tarjetas
  de Áreas de práctica y para no depender solo del dorado en superficies grandes de color.
  También fijos, no cambian con el tema.
- `--color-night` / `--color-night-ink`: acento oscuro fijo (footer, menú móvil).
- **`--color-ink-fixed`**: token especial que **nunca se invierte con el tema**. Existe
  porque varios botones tienen fondo dorado brillante (`bg-gold`) y el texto sobre ellos
  tiene que quedarse oscuro siempre, sin importar si el sitio está en modo claro u oscuro
  (si usaras `text-ink` normal ahí, en modo oscuro `--color-ink` se vuelve claro y el
  texto se volvería invisible sobre el dorado). Se usa en: CTA del hero, CTA del nav
  (desktop y móvil), tarjeta dorada de Áreas de práctica, burbujas doradas del chatbot,
  botón de enviar de Contacto y del modal de testimonios, botón de WhatsApp flotante.
  **Si agregas un botón nuevo con `bg-gold`, usa `text-ink-fixed`, no `text-ink`.**

Tipografía: Playfair Display (titulares, display) + Manrope (cuerpo/UI), sin cambios.

**Dials de diseño** (concepto de la skill `design-taste-frontend`): `VARIANCE 8 / MOTION
9 / DENSITY 3` tras el ciclo de rediseño (subieron desde el `7/6/3` original, a pedido
explícito y repetido del usuario de "más animaciones, más innovador").

### 5.2 Skills instaladas

Instaladas vía `npx skills add <repo>` en `.claude/skills/` (symlinks a `.agents/skills/`).
Las más relevantes para este proyecto:

- `design-taste-frontend` — la dirección de arte base del proyecto, anti-genérico
  (prohíbe em-dash, tres cards iguales, gradiente morado de IA, etc.).
- `gsap-skills` (**nuevo**, `greensock/gsap-skills`): trae 8 sub-skills que se activan
  por separado — `gsap-core`, `gsap-react` (el que más aplica aquí, `useGSAP`/limpieza en
  Next.js), `gsap-timeline`, `gsap-scrolltrigger` (el usado para el indicador de scroll y
  el giro de tono ambiental), `gsap-plugins`, `gsap-performance`, `gsap-utils`,
  `gsap-frameworks` (Vue/Svelte, no aplica).
- `apple-design`, `high-end-visual-design` — nivel de pulido y física de interacción.
- `improve-animations`, `find-animation-opportunities`, `animate`, `review-animations`,
  `animation-vocabulary` — auditoría y construcción de animación.
- `industrial-brutalist-ui`, `minimalist-ui`, `gpt-taste`, `stitch-design-taste`,
  `emil-design-eng`, `brandkit`, `image-to-code`, `imagegen-frontend-web/mobile` —
  instaladas pero no todas se usaron activamente en este proyecto (el sitio usa
  `design-taste-frontend` como base, no el estilo brutalista ni minimalista).

**Regla dura de este proyecto: GSAP y Motion nunca en el mismo componente.** Se pelean
por los mismos frames. Los componentes que usan GSAP (`scroll-progress.tsx`,
`scroll-ambient.tsx`, `nav-link-underline.tsx`) son hojas de cliente aisladas, con
`gsap.context()` + `.revert()` en el cleanup de `useEffect`, y no usan Motion internamente.
Todo lo demás sigue usando Motion (`motion/react`).

Reglas anti-genérico que se siguieron y hay que seguir manteniendo:
- **Em-dash (`—`) completamente prohibido**, en cualquier parte del sitio.
- No más de 1 "eyebrow" cada 3 secciones.
- Máximo 1 marquee por página (el carrusel de "Con la confianza de").
- Contraste WCAG AA siempre, incluso sobre fondos en degradado animado (revisa el ciclo
  completo de la animación, no solo el frame inicial).
- `MotionConfig reducedMotion="user"` en el layout raíz maneja `prefers-reduced-motion`
  globalmente para todo lo que usa Motion. **No** uses `useReducedMotion()` por componente
  para condicionar el `initial` de una animación: causa hydration mismatch.

### 5.3 Mapa de páginas y componentes

```
frontend/src/app/
  layout.tsx          — fonts, script inline de tema (beforeInteractive), MotionConfig
  page.tsx             — home: Hero, QuienesSomos, AreasPractica, TalentoHumano, Equipo,
                          Testimonios, TrustedBy, BlogTeaser, Contacto (9 secciones)
  areas/[slug]/page.tsx — 6 páginas estáticas (SSG), contenido en lib/content.ts
  blog/page.tsx         — listado, filtro por categoría + búsqueda (via searchParams, SSR)
  blog/[slug]/page.tsx  — detalle, dangerouslySetInnerHTML del contenido (autores son
                          internos/confiables, no input público)

frontend/src/components/
  site-chrome.tsx        — envuelve el sitio público en .site-theme, monta ambient-field,
                            ScrollAmbient, ScrollProgress, SiteNav/Footer/WhatsApp/Chatbot
                            (nada de esto se monta dentro de /admin)
  site-nav.tsx            — pill flotante, fondo con color-mix() (.nav-pill, ver 5.4),
                            se compacta con el scroll (Motion useScroll)
  nav-link-underline.tsx  — subrayado del nav que se traza con GSAP al hacer hover
  theme-toggle.tsx        — botón sol/luna, cambia data-theme + persiste en localStorage
  magnetic-button.tsx     — wrapper genérico de botón magnético (useMotionValue, nunca
                            useState), usado en el CTA del hero y del nav
  scroll-progress.tsx     — barra dorada fija arriba, GSAP ScrollTrigger, llena 0-100%
  scroll-ambient.tsx      — gira --scroll-hue en .ambient-field con GSAP ScrollTrigger
                            scrub, no renderiza nada (return null)
  hero.tsx                — BrandCard: el logo (no una foto de persona) sobre un degradado
                            animado, con tilt 3D al mouse (ver 5.5 sobre el aspect ratio)
  quienes-somos.tsx        — Contador: cuenta de 0 al valor real con GSAP/motion al entrar
                            en viewport (animate() de "motion", no de gsap)
  areas-practica.tsx       — bento de 6 tarjetas, cada una con su propio degradado animado
                            (paleta gold/night/terracotta/forest/navy + una neutra)
  talento-humano.tsx, blog-teaser.tsx (con estado vacío propio si el backend no responde,
                            ver 5.6), equipo.tsx (fotos circulares con máscara radial que
                            desvanece el fondo gris de estudio, ver 5.6), testimonios.tsx
                            (mezcla contenido estático + GET /api/testimonios aprobados),
                            testimonio-form-modal.tsx (POST /api/testimonios), trusted-by.tsx
                            (cifras + carrusel de logos, ver 5.6), contacto.tsx (formulario +
                            redes sociales + bloque de privacidad, ver 4.6), site-footer.tsx,
                            whatsapp-float.tsx, chatbot-widget.tsx

frontend/src/lib/
  api.ts        — cliente tipado hacia el backend (NEXT_PUBLIC_API_URL), incluye
                  getTestimoniosAprobados()/crearTestimonio()
  content.ts     — contenido estático real (equipo con bios reales para 2 personas,
                  áreas, testimonios base, empresas con nombre real para alt text)
  site-config.ts — WhatsApp, redes, teléfono/correo, un solo label de CTA ("Agendar asesoría")
  utils.ts       — cn() (clsx)
```

**Ya no existen** (se borraron a propósito, ver sección 3): `gavel-3d.tsx`,
`signature-moment.tsx`. Si los ves mencionados en una versión vieja de este documento o en
el historial de git, ignóralos, no los reconstruyas sin que el usuario lo pida de nuevo.

### 5.4 El truco de `color-mix()` para el nav (por si lo tocas)

El pill del nav (`site-nav.tsx`) necesita un fondo que sea la superficie del tema activo
pero con opacidad animada por scroll. Antes eso eran dos strings `rgba()` hardcodeados en
JS (uno para claro, uno para oscuro), lo cual se rompía apenas se agregó el toggle de
tema. La solución: una clase `.nav-pill` en `globals.css` con
`background-color: color-mix(in srgb, var(--color-surface) calc(var(--nav-alpha, 70) * 1%), transparent)`,
y Motion solo anima la custom property `--nav-alpha` (un número, vía `useTransform` +
`style={{ "--nav-alpha": navAlpha }}`). Así el color base siempre es correcto para el tema
activo sin que el componente necesite saber en qué tema está.

### 5.5 El logo (`public/marca/logo.png`)

Dimensiones reales: **644x559px**, casi cuadrado, ligeramente más ancho que alto. **No es
verticalmente alargado** aunque a veces se vea así si se renderiza pequeño dentro de un
contenedor alto. El bug real que hubo: en el Hero, el logo se mostraba a tamaño fijo
pequeño (144px) centrado dentro de una tarjeta muy alta (`aspect-[4/5]`), dejando mucho
degradado vacío alrededor. Se corrigió usando `aspect-[644/559]` (el aspecto real de la
imagen) + `w-[64%]` del contenedor, así crece proporcionalmente y nunca queda con
letterboxing. Si vas a usar el logo grande en otro lado, usa ese mismo patrón, no un
tamaño fijo en píxeles.

### 5.6 Componentes con estado vacío / de respaldo

- `blog-teaser.tsx`: si `getArticulos()` falla o devuelve `[]`, **ya no retorna `null`**
  (antes desaparecía en silencio, dejando un hueco invisible en la home). Ahora muestra una
  tarjeta con mensaje "Estamos preparando nuestros primeros artículos."
- `trusted-by.tsx`: sección agrandada a pedido explícito (antes era una tira delgada). Ahora
  tiene encabezado propio + 3 cifras reales (20+ años, 800+ casos, 7 empresas aliadas, sin
  inventar ningún número) + el carrusel de logos en tarjetas blancas de 224x112px (antes
  eran más pequeñas y con `grayscale` + baja opacidad, lo que las hacía casi ilegibles
  sobre el fondo oscuro). El carrusel sigue siendo el único marquee de la página (regla de
  la skill: máximo uno).
- `equipo.tsx`: las fotos (que en origen son retratos de estudio con fondo gris ovalado)
  usan `mask-image: radial-gradient(...)` para desvanecer ese fondo gris hacia
  transparente en los bordes, dando la sensación de "sin fondo" sin necesitar una
  herramienta real de recorte/segmentación (no hay ninguna disponible en este entorno).
  **Esto es una aproximación honesta, no una eliminación real de fondo píxel por píxel** —
  déjalo claro si el usuario pregunta por qué el borde no es perfecto.

### 5.7 Assets

Descargados del sitio real (`siejuridicos.com`) con autorización del usuario, en
`frontend/public/marca/` (logo, foto de edificio), `equipo/` (8 fotos reales) y
`confianza/` (7 logos de empresas: Symaa Ingeniería, Connect Americas, Dypsion
International, CK, Tecsai Ingeniería, WorkeR Company, Samval; nombres confirmados
navegando el sitio viejo, usados como `alt` text real).

`frontend/public/fondos/` (5 fotos de Unsplash, arquitectura/biblioteca jurídica) **ya no
se usa en ningún componente** desde que se borró `signature-moment.tsx`. Las imágenes
siguen en disco por si se reutilizan más adelante, pero no están referenciadas.

Dos bios reales del equipo (`content.ts`, campo `bio` de `Tatiana Marcela Bustos Moreno` y
`Jorge Mario Cifuentes Lara`) se copiaron literalmente de sus páginas de perfil publicadas
en siejuridicos.com (contenido real de la firma, no inventado). El resto del equipo no
tiene bio publicada todavía: se muestra solo nombre y cargo. **No inventes bios para
ellos** aunque el diseño "se vería mejor" con más texto, pídele el contenido real al
usuario.

### 5.8 Panel de administración

Vive en la misma app Next.js, bajo `/admin/**`, con un sistema de diseño deliberadamente
distinto al sitio público (funcional, tipo dashboard, sin las animaciones "alto impacto"
del marketing, y **siempre en la paleta clara original**, nunca en `.site-theme` ni afectado
por el toggle de tema del sitio público, ver sección 5.1).

**Auth**: JWT guardado en `localStorage` (no cookie httpOnly). Simplificación consciente
para una herramienta interna con pocos usuarios. El decode de `atob()` en el cliente
(`lib/auth.ts`) es solo para mostrar nombre/rol en la UI — el backend revalida el token en
cada request.

```
frontend/src/lib/
  auth.ts          — decodificarToken, guardarToken, obtenerToken, borrarToken, tokenValido
  auth-context.tsx  — AuthProvider / useAuth (contexto de sesión)
  admin-api.ts      — cliente tipado hacia /api/admin/**, wrapper pedido<T>() inyecta
                       Authorization: Bearer <token>, incluye listarTestimonios()/
                       moderarTestimonio()

frontend/src/components/admin/
  admin-shell.tsx      — sidebar + topbar, navItems filtrados por rol (incluye "Testimonios")
  ui.tsx                — AdminPageHeader, Badge, AdminButton, AdminCard, EmptyState
  rich-text-editor.tsx  — wrapper de Tiptap (immediatelyRender: false)
  articulo-form.tsx     — formulario compartido crear/editar artículo

frontend/src/app/admin/
  login/page.tsx                              — fuera del route group, sin sidebar
  (dashboard)/layout.tsx                       — envuelve todo en <AdminShell>
  (dashboard)/page.tsx                          — resumen con stat cards
  (dashboard)/solicitudes/page.tsx              — filtros, cambio de estado, modal de cita
  (dashboard)/articulos/**                      — listar/crear/editar (incluye borradores)
  (dashboard)/testimonios/page.tsx              — **nuevo**: filtros por estado, aprobar/
                                                   rechazar
  (dashboard)/usuarios/page.tsx                 — solo ADMIN_GENERAL
  (dashboard)/marketing/page.tsx                — listado de suscriptores, solo lectura
  (dashboard)/perfil/page.tsx                   — cambiar contraseña propia
```

**Rutas protegidas por rol en el frontend**: cada página verifica `sesion.rol`, pero la
protección real está en el backend (`SecurityConfig` + `@PreAuthorize`). Verificado con
ambos roles.

## 6. Cómo correr todo

**Backend** (requiere Docker Desktop corriendo):
```bash
run.bat
```
Levanta Postgres+pgAdmin (`docker compose`) y el backend (`spring-boot:run`). Edita las
variables al inicio de `run.bat` para credenciales.

**Frontend**:
```bash
cd frontend
npm run dev
```
Necesita `frontend/.env.local` con `NEXT_PUBLIC_API_URL=http://localhost:8080`.

**Nota sobre Turbopack en esta sesión**: tras muchas ediciones seguidas en caliente, el
dev server con caché persistente de Turbopack empezó a mostrar errores fantasma
(`ReferenceError` de componentes que ya no existían, o que se habían renombrado). El build
de producción (`npm run build`) nunca mintió, siempre reflejó el estado real del código.
Si ves errores raros en consola del navegador que no coinciden con el código actual: para
el server, borra `frontend/.next` y arráncalo de nuevo antes de asumir que hay un bug real.

**pgAdmin**: http://localhost:5050, login `pgadmin@siejuridicos.com` / `admin123`, servidor
"SIE Juridicos (docker)" pre-cargado (contraseña de Postgres: `secret`).

## 7. Variables de entorno / credenciales pendientes

| Variable | Dónde | Estado |
|---|---|---|
| `MAIL_USERNAME` / `MAIL_PASSWORD` | `run.bat` | **Falta.** Gmail App Password de `siejuridicos@gmail.com` (myaccount.google.com/apppasswords) |
| `ANTHROPIC_API_KEY` | `run.bat` | **Falta.** Necesaria para que el chatbot responda de verdad |
| `ADMIN_BOOTSTRAP_EMAIL/PASSWORD` | `run.bat` | Ya configurado: `admin@siejuridicos.com` / `ClaveAdmin2026!` |
| `JWT_SECRET` | `run.bat` | Placeholder de desarrollo, cambiar antes de producción |
| `CORS_ALLOWED_ORIGINS` | backend | Default `http://localhost:3000`; agregar el dominio real al desplegar |

## 8. Datos de prueba ya creados en la BD

- Usuario admin: `admin@siejuridicos.com` / `ClaveAdmin2026!` (rol `ADMIN_GENERAL`).
- Usuario abogado de prueba: `carlos.munoz@siejuridicos.com` / `ClaveAbogado2026!` (rol
  `ABOGADO`).
- 4 artículos de blog **de prueba**. Bórralos o edítalos desde `/admin/articulos` cuando
  tengas contenido real.
- Al menos una solicitud de prueba real con cita agendada.
- `testimonios_publicos` está vacía en cualquier entorno nuevo (la tabla es de la migración
  `V13`, no tiene seed). Para ver el flujo completo, envía uno desde el sitio y apruébalo
  en `/admin/testimonios`.

## 9. Decisiones de alcance que el usuario tomó explícitamente (no las reviertas sin
preguntar)

- JPA (Hibernate) sobre Spring Data JDBC, aunque el proyecto vino inicializado con JDBC.
- Se mantuvo Spring Boot 4.1.0 en vez de bajar a 3.x.
- Citas: agendamiento manual por el abogado, no autoagendamiento público tipo Calendly.
- Recordatorio de cita el mismo día: solo al cliente, no al admin.
- Consentimiento de marketing: checkbox separado del de tratamiento de datos.
- Panel administrativo: paleta y sistema de diseño deliberadamente separados del sitio
  público (incluyendo el modo oscuro, que **no** llega al panel).
- JWT del panel admin en `localStorage`, no cookie httpOnly: simplificación consciente.
- Testimonios: sin subida de archivos/fotos (riesgo de seguridad, se evaluó y se
  descartó), con moderación obligatoria antes de publicarse.
- Elemento 3D (martillo de juez): probado y luego **retirado explícitamente** porque
  "pierde la elegancia del sitio". No reintroducir sin que el usuario lo pida.
- Modo claro/oscuro con toggle manual: pedido explícito, no solo `prefers-color-scheme`.
- Regla de diseño: **nunca un fondo grande de color plano**, siempre degradado animado.
- No se fabrican afirmaciones de "seguridad técnica" (cifrado, certificaciones) en
  Contacto sin que el usuario provea el texto real.
- Frontend en Next.js (no Vite/SPA) específicamente por SEO — el negocio depende de
  posicionar en Google.
