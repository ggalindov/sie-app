"use client";

import { useEffect, useMemo, useState, type ReactNode } from "react";
import { useParams } from "next/navigation";
import Image from "next/image";
import { toast } from "sonner";
import {
  CheckCircle,
  ShieldCheck,
  ArrowRight,
  DownloadSimple,
  XCircle,
  DeviceMobile,
} from "@phosphor-icons/react";
import {
  obtenerResumenFirma,
  verificarIdentidadFirma,
  rechazarFirma,
  reenviarOtpFirma,
  verificarOtpFirma,
  registrarConsentimientoFirma,
  registrarGeolocalizacionFirma,
  registrarIdentidadFirma,
  registrarVerificacionHumanaFirma,
  aceptarAdvertenciaSuplantacionIdentidadFirma,
  aceptarAdvertenciaSuplantacionVerificacionFirma,
  firmarDocumento,
  urlCertificadoEvidenciaFirma,
  ApiError,
  type FirmaResumen,
} from "@/lib/firma-publica-api";
import { SignatureCanvas, type PuntoTrazo } from "@/components/firma/signature-canvas";
import { CameraCapture } from "@/components/firma/camera-capture";
import { VisorDocumentoFirma } from "@/components/firma/visor-documento";

type Paso =
  | "cargando"
  | "error"
  | "solo_celular"
  | "bienvenida"
  | "identidad"
  | "otp"
  | "documento"
  | "consentimiento"
  | "geolocalizacion"
  | "foto_identidad"
  | "verificacion_humana"
  | "firmar"
  | "completado"
  | "rechazado";

const TEXTO_CONSENTIMIENTO =
  "Declaro que he leído el documento mostrado en esta sesión y que, al firmarlo electrónicamente, expreso mi " +
  "consentimiento libre e informado para quedar obligado por su contenido en los mismos términos que si lo " +
  "hubiera firmado de forma manuscrita, de conformidad con la Ley 527 de 1999, el Decreto 2364 de 2012 y la Ley " +
  "2213 de 2022 de la República de Colombia. Entiendo que esta firma, junto con los demás mecanismos técnicos " +
  "empleados en este proceso (verificación de identidad, código de un solo uso, geolocalización, fotografía de " +
  "verificación y trazabilidad del dispositivo), queda registrada como evidencia de la autoría e integridad de " +
  "mi firma, y que SIE Jurídicos conservará dicha evidencia conforme a la normativa de protección de datos " +
  "aplicable (Ley 1581 de 2012).";

// Redacción propia: NO se cita un número de artículo del Código Penal sin confirmarlo con
// certeza (ver nota en el trabajo paralelo del backend sobre por qué es más seguro ser
// impreciso en el número que citar mal un artículo en un documento legal real).
const TEXTO_ADVERTENCIA_SUPLANTACION =
  "Al continuar, declaras que la fotografía que vas a tomar corresponde a tu propia identidad y, si aplica, al " +
  "documento que tienes físicamente en tus manos en este momento, tomada en vivo con la cámara de este " +
  "dispositivo -- no una foto guardada, una captura de pantalla ni el documento o rostro de otra persona. " +
  "Suplantar la identidad de otra persona en un trámite de firma electrónica puede constituir un delito conforme " +
  "a la legislación penal colombiana. Esta aceptación queda registrada como parte de la evidencia del proceso.";

function huellaDeDispositivo(): string {
  // No biométrico: combinación estable de características del navegador/pantalla, igual
  // que un identificador de sesión -- ver ProxyHeuristicsAnalyzer/EvidenceCaptureService
  // en el backend, que la trata únicamente como evidencia técnica adicional, nunca como
  // mecanismo de verificación de identidad por sí sola.
  if (typeof window === "undefined") return "";
  const partes = [
    navigator.userAgent,
    navigator.language,
    `${window.screen.width}x${window.screen.height}`,
    Intl.DateTimeFormat().resolvedOptions().timeZone,
    String(navigator.hardwareConcurrency ?? ""),
  ];
  return partes.join("|");
}

// Bug real encontrado en producción: huellaDispositivo se mandaba cruda (navigator.userAgent
// completo incluido) a una columna VARCHAR(64) -- en cualquier celular real el user-agent
// por sí solo ya supera los 64 caracteres, así que CADA firma real fallaba con "value too
// long for type character varying(64)" (un 500 traducido a "El servicio no está disponible"
// por el manejador genérico de errores de base de datos). En las pruebas de esta sesión
// nunca se notó porque se mandaba una huella sintética corta por curl, no una real de
// navegador. El arreglo correcto es justo lo que el nombre del campo ya decía que debía
// ser -- una HUELLA (hash), no el texto crudo: SHA-256 hex mide siempre 64 caracteres,
// exactamente el límite de la columna.
async function sha256Hex(texto: string): Promise<string> {
  const datos = new TextEncoder().encode(texto);
  const hash = await crypto.subtle.digest("SHA-256", datos);
  return Array.from(new Uint8Array(hash))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

// Pedido explícito del usuario: firmar solo debe poder hacerse desde un celular, nunca
// desde un PC de escritorio. Este chequeo del lado del cliente es solo para mostrar un
// mensaje claro en vez de que cada paso falle con un error genérico -- el bloqueo real,
// el que no se puede saltar abriendo las herramientas de desarrollador, vive en el
// servidor (ver MobileOnlySignatureFilter), con el mismo regex "Mobi".
function esDispositivoMovil(): boolean {
  if (typeof navigator === "undefined") return true;
  return /Mobi/i.test(navigator.userAgent);
}

export default function FirmarPage() {
  const { token } = useParams<{ token: string }>();
  const [paso, setPaso] = useState<Paso>("cargando");
  const [resumen, setResumen] = useState<FirmaResumen | null>(null);
  const [cargandoAccion, setCargandoAccion] = useState(false);

  const [numeroDocumento, setNumeroDocumento] = useState("");
  const [codigoOtp, setCodigoOtp] = useState("");
  const [consentimientoMarcado, setConsentimientoMarcado] = useState(false);
  const [errorGeolocalizacion, setErrorGeolocalizacion] = useState<string | null>(null);
  const [fotoIdentidad, setFotoIdentidad] = useState<Blob | null>(null);
  const [fotoHumana, setFotoHumana] = useState<Blob | null>(null);
  const [advertenciaIdentidadMarcada, setAdvertenciaIdentidadMarcada] = useState(false);
  const [advertenciaVerificacionMarcada, setAdvertenciaVerificacionMarcada] = useState(false);
  const [firmaPng, setFirmaPng] = useState<Blob | null>(null);
  const [trazoVectorial, setTrazoVectorial] = useState<PuntoTrazo[] | null>(null);

  const [mostrarRechazo, setMostrarRechazo] = useState(false);
  const [motivoRechazo, setMotivoRechazo] = useState("");
  const [rechazando, setRechazando] = useState(false);

  const huella = useMemo(() => huellaDeDispositivo(), []);

  useEffect(() => {
    obtenerResumenFirma(token)
      .then((r) => {
        setResumen(r);
        const pasoCalculado = pasoDesdeResumen(r);
        const yaTermino = pasoCalculado === "completado" || pasoCalculado === "rechazado";
        setPaso(!yaTermino && !esDispositivoMovil() ? "solo_celular" : pasoCalculado);
      })
      .catch(() => setPaso("error"));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  function pasoDesdeResumen(r: FirmaResumen): Paso {
    if (r.firmado) return "completado";
    if (r.estadoFirmante === "RECHAZADO") return "rechazado";
    if (!r.identidadVerificada) return "bienvenida";
    if (!r.otpVerificado) return "otp";
    if (!r.consentimientoRegistrado) return "documento";
    // Bug real: faltaba este chequeo -- si el firmante recargaba la página justo en el
    // paso de geolocalización (antes de completarlo), este resolver lo saltaba directo a
    // foto_identidad porque no revisaba geolocalizacionRegistrada, perdiendo esa evidencia
    // para siempre en ese firmante (nunca se le volvía a pedir).
    if (!r.geolocalizacionRegistrada) return "geolocalizacion";
    if (!r.identidadRegistrada) return "foto_identidad";
    if (!r.verificacionHumanaRegistrada) return "verificacion_humana";
    return "firmar";
  }

  async function onEnviarIdentidad() {
    if (!numeroDocumento.trim()) {
      toast.error("Ingresa tu número de documento.");
      return;
    }
    setCargandoAccion(true);
    try {
      const r = await verificarIdentidadFirma(token, numeroDocumento.trim());
      setResumen(r);
      setPaso("otp");
      toast.success("Te enviamos un código de verificación.");
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo verificar tu identidad.");
    } finally {
      setCargandoAccion(false);
    }
  }

  async function onReenviarOtp() {
    try {
      await reenviarOtpFirma(token);
      toast.success("Te reenviamos el código.");
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo reenviar el código.");
    }
  }

  async function onVerificarOtp() {
    if (codigoOtp.trim().length < 4) {
      toast.error("Ingresa el código recibido.");
      return;
    }
    setCargandoAccion(true);
    try {
      const r = await verificarOtpFirma(token, codigoOtp.trim());
      setResumen(r);
      setPaso("documento");
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Código incorrecto o vencido.");
    } finally {
      setCargandoAccion(false);
    }
  }

  async function onAceptarConsentimiento() {
    if (!consentimientoMarcado) {
      toast.error("Marca la casilla para aceptar el consentimiento.");
      return;
    }
    setCargandoAccion(true);
    try {
      const r = await registrarConsentimientoFirma(token, TEXTO_CONSENTIMIENTO, true);
      setResumen(r);
      setPaso("geolocalizacion");
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo registrar el consentimiento.");
    } finally {
      setCargandoAccion(false);
    }
  }

  // Pedido explícito del usuario: la ubicación deja de ser un dato "mejor esfuerzo" --
  // ahora es obligatoria para continuar. Antes, negarla o no tenerla disponible registraba
  // el rechazo como evidencia y avanzaba igual a la foto de identidad; ahora se queda en
  // este mismo paso con un mensaje claro y la opción de reintentar (el backend también
  // bloquea firmar() sin ubicación capturada, ver PublicSignatureService -- esto no es solo
  // una validación de interfaz que se pueda saltar llamando la API directo).
  function onPedirGeolocalizacion() {
    setCargandoAccion(true);
    setErrorGeolocalizacion(null);
    if (!navigator.geolocation) {
      registrarGeolocalizacionFirma(token, { lat: null, lon: null, precisionM: null, rechazada: true })
        .catch(() => undefined)
        .finally(() => {
          setCargandoAccion(false);
          setErrorGeolocalizacion("Tu navegador no soporta compartir ubicación. Prueba desde otro navegador o dispositivo para poder continuar.");
        });
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        registrarGeolocalizacionFirma(token, {
          lat: pos.coords.latitude,
          lon: pos.coords.longitude,
          precisionM: pos.coords.accuracy,
          rechazada: false,
        })
          .then((r) => {
            setResumen(r);
            setPaso("foto_identidad");
          })
          .catch(() => setErrorGeolocalizacion("No se pudo guardar tu ubicación. Inténtalo de nuevo."))
          .finally(() => setCargandoAccion(false));
      },
      () => {
        registrarGeolocalizacionFirma(token, { lat: null, lon: null, precisionM: null, rechazada: true })
          .catch(() => undefined)
          .finally(() => {
            setCargandoAccion(false);
            setErrorGeolocalizacion(
              "Necesitamos tu ubicación para continuar. Revisa que le hayas dado permiso de ubicación a este sitio en tu navegador y vuelve a intentarlo.",
            );
          });
      },
      { timeout: 8000 },
    );
  }

  async function onGuardarFotoIdentidad() {
    if (!advertenciaIdentidadMarcada) {
      toast.error("Marca la casilla de advertencia antes de continuar.");
      return;
    }
    if (!fotoIdentidad) {
      toast.error("Toma la foto de tu documento de identidad.");
      return;
    }
    setCargandoAccion(true);
    try {
      if (!resumen?.advertenciaSuplantacionIdentidadAceptada) {
        await aceptarAdvertenciaSuplantacionIdentidadFirma(token);
      }
      const r = await registrarIdentidadFirma(token, fotoIdentidad);
      setResumen(r);
      setPaso("verificacion_humana");
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo guardar la foto.");
    } finally {
      setCargandoAccion(false);
    }
  }

  async function onGuardarFotoHumana() {
    if (!advertenciaVerificacionMarcada) {
      toast.error("Marca la casilla de advertencia antes de continuar.");
      return;
    }
    if (!fotoHumana) {
      toast.error("Tómate la foto de verificación.");
      return;
    }
    setCargandoAccion(true);
    try {
      if (!resumen?.advertenciaSuplantacionVerificacionAceptada) {
        await aceptarAdvertenciaSuplantacionVerificacionFirma(token);
      }
      const r = await registrarVerificacionHumanaFirma(token, fotoHumana);
      setResumen(r);
      setPaso("firmar");
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo guardar la foto.");
    } finally {
      setCargandoAccion(false);
    }
  }

  async function onFirmar() {
    if (!firmaPng) {
      toast.error("Dibuja tu firma antes de continuar.");
      return;
    }
    setCargandoAccion(true);
    try {
      const r = await firmarDocumento(token, firmaPng, {
        huellaDispositivo: await sha256Hex(huella),
        contextoDispositivoJson: JSON.stringify({ idioma: navigator.language, zonaHoraria: Intl.DateTimeFormat().resolvedOptions().timeZone }),
        trazoVectorialJson: trazoVectorial ? JSON.stringify(trazoVectorial) : undefined,
      });
      setResumen(r);
      setPaso("completado");
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo registrar tu firma.");
    } finally {
      setCargandoAccion(false);
    }
  }

  async function onRechazar() {
    setRechazando(true);
    try {
      const r = await rechazarFirma(token, motivoRechazo.trim() || undefined);
      setResumen(r);
      setPaso("rechazado");
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo registrar tu rechazo.");
    } finally {
      setRechazando(false);
    }
  }

  // Disponible en cualquier paso donde todavía no haya firmado ni rechazado: declinar no
  // debe exigir haber completado identidad/OTP primero (ver PublicSignatureService#rechazar).
  const pasosConOpcionDeRechazar: Paso[] = [
    "bienvenida",
    "otp",
    "documento",
    "geolocalizacion",
    "foto_identidad",
    "verificacion_humana",
    "firmar",
  ];

  return (
    <div className="flex min-h-[100dvh] flex-col bg-[#faf9f6] px-4 py-8 sm:py-12">
      <div className="mx-auto w-full max-w-lg">
        <div className="mb-8 flex items-center justify-center gap-2.5">
          <Image src="/marca/logo.png" alt="SIE Jurídicos" width={36} height={31} />
          <span className="font-display text-lg text-[#1a1a1a]">SIE Jurídicos</span>
        </div>

        {paso === "cargando" && <p className="text-center text-sm text-[#6b6b6b]">Cargando...</p>}

        {paso === "error" && (
          <Tarjeta>
            <p className="text-center text-sm text-[#6b6b6b]">
              Este enlace no es válido, ya fue usado o venció. Solicita a SIE Jurídicos que te envíe uno nuevo.
            </p>
          </Tarjeta>
        )}

        {paso === "solo_celular" && (
          <Tarjeta className="text-center">
            <DeviceMobile className="mx-auto h-10 w-10 text-[#b5922e]" weight="bold" />
            <h2 className="mt-3 font-display text-lg text-[#1a1a1a]">Abre este enlace desde tu celular</h2>
            <p className="mt-2 text-sm text-[#6b6b6b]">
              Por seguridad y validez legal, la firma electrónica de este documento solo se puede completar desde un
              teléfono móvil. Copia este enlace y ábrelo en el navegador de tu celular para continuar.
            </p>
          </Tarjeta>
        )}

        {paso !== "cargando" && paso !== "error" && paso !== "solo_celular" && resumen && (
          <>
            <Tarjeta>
              <p className="text-xs font-medium uppercase tracking-wide text-[#b5922e]">Documento</p>
              <p className="mt-1 font-display text-xl text-[#1a1a1a]">{resumen.documentoNombre}</p>
              <p className="mt-1 text-sm text-[#6b6b6b]">Firmante: {resumen.nombreFirmante}</p>
            </Tarjeta>

            {paso === "bienvenida" && (
              <Tarjeta className="mt-4">
                <h2 className="font-display text-lg text-[#1a1a1a]">Verifiquemos tu identidad</h2>
                <p className="mt-2 text-sm text-[#6b6b6b]">
                  Ingresa tu número de cédula o NIT, tal como quedó registrado para esta firma.
                </p>
                <input
                  value={numeroDocumento}
                  onChange={(e) => setNumeroDocumento(e.target.value)}
                  placeholder="Número de documento"
                  className="mt-4 w-full rounded-lg border border-[#e2ddd1] bg-white px-4 py-3 text-sm outline-none focus:border-[#b5922e]"
                />
                <BotonContinuar onClick={onEnviarIdentidad} cargando={cargandoAccion} />
              </Tarjeta>
            )}

            {paso === "otp" && (
              <Tarjeta className="mt-4">
                <h2 className="font-display text-lg text-[#1a1a1a]">Código de verificación</h2>
                <p className="mt-2 text-sm text-[#6b6b6b]">Te enviamos un código de un solo uso. Ingrésalo para continuar.</p>
                <input
                  value={codigoOtp}
                  onChange={(e) => setCodigoOtp(e.target.value)}
                  placeholder="Código de 6 dígitos"
                  inputMode="numeric"
                  className="mt-4 w-full rounded-lg border border-[#e2ddd1] bg-white px-4 py-3 text-center text-lg tracking-[0.3em] outline-none focus:border-[#b5922e]"
                />
                <button type="button" onClick={onReenviarOtp} className="mt-2 text-xs font-medium text-[#b5922e] hover:underline">
                  No me llegó el código, reenviarlo
                </button>
                <BotonContinuar onClick={onVerificarOtp} cargando={cargandoAccion} />
              </Tarjeta>
            )}

            {paso === "documento" && (
              <Tarjeta className="mt-4">
                <h2 className="font-display text-lg text-[#1a1a1a]">Revisa el documento</h2>
                <p className="mt-2 text-sm text-[#6b6b6b]">
                  Esta es una vista de solo lectura. El archivo descargable estará disponible una vez completes tu firma.
                </p>
                <div className="mt-4">
                  <VisorDocumentoFirma token={token} />
                </div>
                <div className="mt-5 rounded-lg bg-[#faf6ea] p-4 text-xs text-[#6b5b2e]">{TEXTO_CONSENTIMIENTO}</div>
                <label className="mt-4 flex items-start gap-2.5 text-sm text-[#1a1a1a]">
                  <input
                    type="checkbox"
                    checked={consentimientoMarcado}
                    onChange={(e) => setConsentimientoMarcado(e.target.checked)}
                    className="mt-0.5"
                  />
                  He leído el documento y acepto firmarlo electrónicamente, con las condiciones descritas arriba.
                </label>
                <BotonContinuar onClick={onAceptarConsentimiento} cargando={cargandoAccion} />
              </Tarjeta>
            )}

            {paso === "geolocalizacion" && (
              <Tarjeta className="mt-4">
                <h2 className="font-display text-lg text-[#1a1a1a]">Confirma tu ubicación</h2>
                <p className="mt-2 text-sm text-[#6b6b6b]">
                  Tu navegador te pedirá permiso de ubicación. Es un requisito de este proceso de firma: hace falta
                  para poder continuar.
                </p>
                {errorGeolocalizacion && (
                  <p className="mt-3 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{errorGeolocalizacion}</p>
                )}
                <BotonContinuar
                  onClick={onPedirGeolocalizacion}
                  cargando={cargandoAccion}
                  texto={errorGeolocalizacion ? "Reintentar" : "Continuar"}
                />
              </Tarjeta>
            )}

            {paso === "foto_identidad" && (
              <Tarjeta className="mt-4">
                <h2 className="font-display text-lg text-[#1a1a1a]">Foto de tu documento de identidad</h2>
                <p className="mt-2 rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-900">
                  Toma la foto en vivo, directo del documento físico que tienes en tus manos -- no de una foto
                  guardada, una captura de pantalla ni un documento de otra persona. Esta foto queda registrada como
                  evidencia; si no corresponde a un documento real tomado en el momento, la firma pierde su validez
                  legal.
                </p>
                <div className="mt-3 rounded-lg bg-[#faf6ea] p-3 text-xs text-[#6b5b2e]">
                  {TEXTO_ADVERTENCIA_SUPLANTACION}
                </div>
                <label className="mt-3 flex items-start gap-2.5 text-sm text-[#1a1a1a]">
                  <input
                    type="checkbox"
                    checked={advertenciaIdentidadMarcada}
                    onChange={(e) => setAdvertenciaIdentidadMarcada(e.target.checked)}
                    className="mt-0.5"
                  />
                  Entiendo y acepto lo anterior.
                </label>
                <div className="mt-4">
                  <CameraCapture
                    etiqueta="Enfoca tu cédula o documento de identidad, que se vea legible."
                    onCapturar={setFotoIdentidad}
                  />
                </div>
                <BotonContinuar onClick={onGuardarFotoIdentidad} cargando={cargandoAccion} />
              </Tarjeta>
            )}

            {paso === "verificacion_humana" && (
              <Tarjeta className="mt-4">
                <h2 className="font-display text-lg text-[#1a1a1a]">Una foto tuya</h2>
                <p className="mt-2 text-sm text-[#6b6b6b]">
                  Es evidencia adicional de seguridad, no reconocimiento facial: solo confirma que una persona real
                  está completando esta firma.
                </p>
                <p className="mt-2 rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-900">
                  Debe ser una selfie tuya, tomada en vivo con la cámara ahora mismo -- no la foto de otra persona ni
                  una imagen guardada. Si esta foto no corresponde a quien está firmando, la firma pierde su validez
                  legal.
                </p>
                <div className="mt-3 rounded-lg bg-[#faf6ea] p-3 text-xs text-[#6b5b2e]">
                  {TEXTO_ADVERTENCIA_SUPLANTACION}
                </div>
                <label className="mt-3 flex items-start gap-2.5 text-sm text-[#1a1a1a]">
                  <input
                    type="checkbox"
                    checked={advertenciaVerificacionMarcada}
                    onChange={(e) => setAdvertenciaVerificacionMarcada(e.target.checked)}
                    className="mt-0.5"
                  />
                  Entiendo y acepto lo anterior.
                </label>
                <div className="mt-4">
                  <CameraCapture etiqueta="Mira hacia la cámara." onCapturar={setFotoHumana} facingMode="user" />
                </div>
                <BotonContinuar onClick={onGuardarFotoHumana} cargando={cargandoAccion} />
              </Tarjeta>
            )}

            {paso === "firmar" && (
              <Tarjeta className="mt-4">
                <h2 className="font-display text-lg text-[#1a1a1a]">Tu firma</h2>
                <p className="mt-2 text-sm text-[#6b6b6b]">Dibuja tu firma en el recuadro.</p>
                <div className="mt-4">
                  <SignatureCanvas
                    onCambio={(png, trazo) => {
                      setFirmaPng(png);
                      setTrazoVectorial(trazo);
                    }}
                  />
                </div>
                <BotonContinuar onClick={onFirmar} cargando={cargandoAccion} texto="Firmar documento" />
              </Tarjeta>
            )}

            {paso === "completado" && (
              <Tarjeta className="mt-4 text-center">
                <CheckCircle className="mx-auto h-10 w-10 text-emerald-600" weight="fill" />
                <h2 className="mt-3 font-display text-lg text-[#1a1a1a]">Tu firma quedó registrada</h2>
                <p className="mt-2 text-sm text-[#6b6b6b]">
                  Te enviaremos el documento final firmado por correo en cuanto todas las partes completen su firma.
                </p>
                <div className="mt-5 flex items-center justify-center gap-1.5 text-xs text-emerald-700">
                  <ShieldCheck className="h-4 w-4" weight="bold" /> Firma sellada con validez legal (Ley 527 de 1999)
                </div>
                <a
                  href={urlCertificadoEvidenciaFirma(token)}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="mt-5 inline-flex items-center gap-2 rounded-lg bg-[#1a1a1a] px-4 py-2.5 text-sm font-medium text-white"
                >
                  <DownloadSimple className="h-4 w-4" weight="bold" /> Descargar mi certificado de evidencia
                </a>
              </Tarjeta>
            )}

            {paso === "rechazado" && (
              <Tarjeta className="mt-4 text-center">
                <XCircle className="mx-auto h-10 w-10 text-[#8a2a2a]" weight="fill" />
                <h2 className="mt-3 font-display text-lg text-[#1a1a1a]">Quedó registrado que no firmarás este documento</h2>
                <p className="mt-2 text-sm text-[#6b6b6b]">
                  Avisamos a SIE Jurídicos. Si fue un error o cambias de opinión, comunícate directamente con ellos
                  para que te envíen un enlace nuevo.
                </p>
              </Tarjeta>
            )}

            {mostrarRechazo && pasosConOpcionDeRechazar.includes(paso) && (
              <Tarjeta className="mt-4">
                <h2 className="font-display text-base text-[#1a1a1a]">¿Seguro que no quieres firmar?</h2>
                <p className="mt-2 text-sm text-[#6b6b6b]">
                  Esta acción anula el proceso de firma de este documento y se le avisa a SIE Jurídicos. Puedes
                  contarnos por qué, si quieres (opcional).
                </p>
                <textarea
                  value={motivoRechazo}
                  onChange={(e) => setMotivoRechazo(e.target.value)}
                  placeholder="Motivo (opcional)"
                  rows={3}
                  className="mt-3 w-full rounded-lg border border-[#e2ddd1] bg-white px-4 py-3 text-sm outline-none focus:border-[#b5922e]"
                />
                <div className="mt-3 flex gap-2">
                  <button
                    type="button"
                    onClick={() => setMostrarRechazo(false)}
                    className="flex-1 rounded-lg bg-[#1a1a1a]/5 px-4 py-2.5 text-sm font-medium text-[#1a1a1a]"
                  >
                    Seguir firmando
                  </button>
                  <button
                    type="button"
                    onClick={onRechazar}
                    disabled={rechazando}
                    className="flex-1 rounded-lg bg-[#8a2a2a] px-4 py-2.5 text-sm font-semibold text-white disabled:opacity-60"
                  >
                    {rechazando ? "Procesando..." : "Sí, no voy a firmar"}
                  </button>
                </div>
              </Tarjeta>
            )}

            {!mostrarRechazo && pasosConOpcionDeRechazar.includes(paso) && (
              <button
                type="button"
                onClick={() => setMostrarRechazo(true)}
                className="mt-4 w-full text-center text-xs font-medium text-[#8a2a2a] hover:underline"
              >
                No quiero firmar este documento
              </button>
            )}
          </>
        )}
      </div>
    </div>
  );
}

function Tarjeta({ children, className = "" }: { children: ReactNode; className?: string }) {
  return <div className={`rounded-2xl bg-white p-6 shadow-sm ring-1 ring-[#eee8db] ${className}`}>{children}</div>;
}

function BotonContinuar({
  onClick,
  cargando,
  texto = "Continuar",
}: {
  onClick: () => void;
  cargando: boolean;
  texto?: string;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={cargando}
      className="mt-5 flex w-full items-center justify-center gap-2 rounded-lg bg-[#b5922e] px-4 py-3 text-sm font-semibold text-[#1a1a1a] transition-opacity disabled:opacity-60"
    >
      {cargando ? "Procesando..." : texto}
      {!cargando && <ArrowRight className="h-4 w-4" weight="bold" />}
    </button>
  );
}
