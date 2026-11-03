package sie.siejuridicos.vigilancia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sie.siejuridicos.caso.Caso;
import sie.siejuridicos.caso.RadicadoUtil;
import sie.siejuridicos.correo.EmailService;
import sie.siejuridicos.usuario.UsuarioInterno;
import sie.siejuridicos.usuario.UsuarioInternoRepository;
import sie.siejuridicos.vigilancia.dto.ActuacionRegistradaResponse;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// Reporte diario integral de movimientos procesales y boletines judiciales (pedido explícito del usuario:
// "el correo tiene que enviar todos los movimientos por caso detectados en el ultimo dia que aun no se le hayan
// enviado, y si encontro boletines de los ultimos dias que se los envie, por ejemplo UIS envia asi, lupa envia asi").
//
// Destinatarios:
//  - Gerencia: TODOS los casos de la firma con movimientos o boletines nuevos detectados, diario a las 10:00 AM.
//  - Cada abogado: SOLO sus casos asignados con movimientos o boletines nuevos detectados, diario a las 10:00 AM.
//  - Clientes: semanal (viernes) con boletines coincidentes y link directo a consulta.
@Service
public class BoletinJudicialDigestService {

    private static final Logger log = LoggerFactory.getLogger(BoletinJudicialDigestService.class);
    private static final DateTimeFormatter FECHA_CORTO = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter FECHA_LEGIBLE =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", new Locale("es", "CO"));

    private final BoletinJudicialCacheRepository boletinRepository;
    private final ActuacionJudicialRegistradaRepository actuacionRepository;
    private final UsuarioInternoRepository usuarioInternoRepository;
    private final EmailService emailService;
    private final String correoGerencia;
    private final boolean envioClientesActivo;

    public BoletinJudicialDigestService(BoletinJudicialCacheRepository boletinRepository,
                                         ActuacionJudicialRegistradaRepository actuacionRepository,
                                         UsuarioInternoRepository usuarioInternoRepository,
                                         EmailService emailService,
                                         @Value("${app.correo.gerencia}") String correoGerencia,
                                         @Value("${app.vigilancia.envio-clientes-activo:false}") boolean envioClientesActivo) {
        this.boletinRepository = boletinRepository;
        this.actuacionRepository = actuacionRepository;
        this.usuarioInternoRepository = usuarioInternoRepository;
        this.emailService = emailService;
        this.correoGerencia = correoGerencia;
        this.envioClientesActivo = envioClientesActivo;
    }

    // Diario 10:00 AM (ver BoletinJudicialScheduler). Envía a Gerencia TODOS los movimientos procesales
    // y boletines detectados que aún no hayan sido notificados.
    @Transactional
    public void enviarDigestGerente() {
        List<ActuacionJudicialRegistrada> actuacionesPendientes = actuacionRepository.listarPendientesNotificarStaff();
        List<BoletinJudicialCache> boletinesPendientes = boletinRepository.listarPendientesGerente();

        if (actuacionesPendientes.isEmpty() && boletinesPendientes.isEmpty()) {
            log.info("Reporte diario de movimientos procesales a gerencia: sin novedades ni boletines pendientes hoy.");
            return;
        }

        String fechaHoy = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String cuerpo = construirCuerpoReporteDiarioStaff(
                "Gerencia General",
                actuacionesPendientes,
                boletinesPendientes,
                "Resumen consolidado de todos los movimientos procesales y boletines oficiales registrados en las últimas 24 horas para los expedientes bajo vigilancia de la firma."
        );

        List<EmailService.Adjunto> adjuntos = adjuntosSegurosDe(boletinesPendientes);
        String asunto = "REPORTE DE MOVIMIENTO PROCESAL - SIE JURÍDICOS (" + fechaHoy + ")";
        emailService.enviarDigestBoletinGerente(correoGerencia, cuerpo, adjuntos);

        LocalDateTime ahora = LocalDateTime.now();
        actuacionesPendientes.forEach(a -> {
            a.setNotificadaStaff(true);
            a.setFechaNotificadaStaff(ahora);
        });
        actuacionRepository.saveAll(actuacionesPendientes);

        boletinesPendientes.forEach(b -> b.setEnviadoGerente(true));
        boletinRepository.saveAll(boletinesPendientes);

        log.info("Reporte diario de movimientos procesales a gerencia ENVIADO: {} actuación(es) y {} boletín(es).",
                actuacionesPendientes.size(), boletinesPendientes.size());
    }

    // Diario 10:00 AM: envía a cada abogado activo el reporte de sus casos asignados
    @Transactional
    public void enviarDigestAbogados() {
        List<UsuarioInterno> abogados = usuarioInternoRepository.findByActivoTrueOrderByNombreAsc();
        String fechaHoy = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        LocalDateTime ahora = LocalDateTime.now();

        for (UsuarioInterno abogado : abogados) {
            if (abogado.getCorreo() == null || abogado.getCorreo().isBlank()) {
                continue;
            }
            List<ActuacionJudicialRegistrada> actuacionesAbogado =
                    actuacionRepository.listarPendientesNotificarPorAbogado(abogado.getId());
            List<BoletinJudicialCache> boletinesAbogado =
                    boletinRepository.listarPendientesPorAbogado(abogado.getId());

            if (actuacionesAbogado.isEmpty() && boletinesAbogado.isEmpty()) {
                continue;
            }

            String cuerpo = construirCuerpoReporteDiarioStaff(
                    abogado.getNombre(),
                    actuacionesAbogado,
                    boletinesAbogado,
                    "Resumen de movimientos procesales y boletines oficiales registrados en las últimas 24 horas en tus expedientes asignados."
            );

            List<EmailService.Adjunto> adjuntos = adjuntosSegurosDe(boletinesAbogado);
            String asunto = "REPORTE DE MOVIMIENTO PROCESAL - " + abogado.getNombre().toUpperCase() + " (" + fechaHoy + ")";
            emailService.enviarDigestBoletinAbogado(abogado.getNombre(), abogado.getCorreo(), cuerpo, adjuntos);

            actuacionesAbogado.forEach(a -> {
                a.setNotificadaStaff(true);
                a.setFechaNotificadaStaff(ahora);
            });
            actuacionRepository.saveAll(actuacionesAbogado);

            boletinesAbogado.forEach(b -> b.setEnviadoAbogado(true));
            boletinRepository.saveAll(boletinesAbogado);

            log.info("Reporte diario de movimientos procesales a abogado '{}' ENVIADO: {} actuación(es), {} boletín(es).",
                    abogado.getNombre(), actuacionesAbogado.size(), boletinesAbogado.size());
        }
    }

    // Semanal clientes (viernes)
    @Transactional
    public void enviarDigestClientes() {
        if (!envioClientesActivo) {
            log.info("[SEGURIDAD ACTIVA] Digest de boletines a clientes reales APAGADO por configuración (app.vigilancia.envio-clientes-activo=false). Cero correos a clientes reales.");
            return;
        }
        List<Long> idsCasos = boletinRepository.listarIdsCasosConPendientesCliente();
        for (Long casoId : idsCasos) {
            List<BoletinJudicialCache> pendientes =
                    boletinRepository.findByCaso_IdAndEnviadoClienteFalseAndCoincideNombreActuacionTrue(casoId);
            if (pendientes.isEmpty()) {
                continue;
            }
            Caso caso = pendientes.get(0).getCaso();
            if (caso.getCliente() == null || caso.getCliente().getCorreo() == null || caso.getRadicadoId() == null) {
                continue;
            }
            String nombreMostrado = caso.getNombreEnHoja() != null ? caso.getNombreEnHoja() : caso.getCliente().getNombre();
            String cuerpo = construirCuerpoDigestCliente(caso, nombreMostrado, pendientes,
                    "Esta semana hubo novedades en tu proceso. Te resumimos abajo la última actualización y los "
                            + "documentos oficiales publicados por el despacho.");
            List<EmailService.Adjunto> adjuntos = adjuntosSegurosDe(pendientes);
            boolean exito = emailService.enviarDigestBoletinCliente(
                    nombreMostrado, caso.getCliente().getCorreo(), caso.getRadicadoId(), cuerpo, adjuntos);
            if (exito) {
                pendientes.forEach(b -> b.setEnviadoCliente(true));
                boletinRepository.saveAll(pendientes);
            }
        }
        log.info("Digest semanal de boletines judiciales a clientes: {} caso(s) procesado(s).", idsCasos.size());
    }

    public boolean probarDigestInterno(Long casoId, String correoDestino) {
        return probarReporteDiario(correoDestino);
    }

    public boolean probarDigestCliente(Long casoId, String correoDestino) {
        List<BoletinJudicialCache> boletines = boletinRepository.findByCaso_Id(casoId);
        if (boletines.isEmpty()) {
            return false;
        }
        Caso caso = boletines.get(0).getCaso();
        if (caso.getRadicadoId() == null) {
            return false;
        }
        String nombreMostrado = caso.getNombreEnHoja() != null ? caso.getNombreEnHoja() : caso.getCliente().getNombre();
        String cuerpo = construirCuerpoDigestCliente(caso, nombreMostrado, boletines,
                "PRUEBA -- formato de reporte al cliente (boletines de prueba).");
        return emailService.enviarDigestBoletinCliente(
                nombreMostrado, correoDestino, caso.getRadicadoId(), cuerpo, adjuntosSegurosDe(boletines));
    }

    // Prueba en vivo sin alterar marcas de enviado
    public boolean probarReporteDiario(String correoDestino) {
        List<ActuacionJudicialRegistrada> actuaciones = actuacionRepository.findAll().stream()
                .sorted(Comparator.comparing(ActuacionJudicialRegistrada::getId).reversed())
                .limit(10)
                .toList();
        List<BoletinJudicialCache> boletines = boletinRepository.findAll().stream()
                .sorted(Comparator.comparing(BoletinJudicialCache::getId).reversed())
                .limit(5)
                .toList();

        if (actuaciones.isEmpty() && boletines.isEmpty()) {
            return false;
        }

        String fechaHoy = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        String cuerpo = construirCuerpoReporteDiarioStaff(
                "Prueba Formato",
                actuaciones,
                boletines,
                "PRUEBA DE FORMATO OPERATIVO -- Demostración del reporte diario integral con diseño estilo IUS Tech y Lupa Control."
        );
        emailService.enviarDigestBoletinGerente(correoDestino, cuerpo, adjuntosSegurosDe(boletines));
        return true;
    }

    // Adjuntos que no sobrepasen 15MB en total para evitar rebotes de correo
    private List<EmailService.Adjunto> adjuntosSegurosDe(List<BoletinJudicialCache> boletines) {
        List<EmailService.Adjunto> adjuntos = new ArrayList<>();
        long tamanoTotal = 0;
        long limiteMaximo = 15 * 1024 * 1024; // 15 MB

        for (BoletinJudicialCache b : boletines) {
            byte[] bytes = b.getContenidoBytes();
            if (bytes != null && bytes.length > 0) {
                if (tamanoTotal + bytes.length <= limiteMaximo) {
                    adjuntos.add(new EmailService.Adjunto(b.getNombreArchivo(), bytes));
                    tamanoTotal += bytes.length;
                } else {
                    log.info("Boletín '{}' omitido como archivo adjunto por superar el límite total de 15MB (descarga vía link).",
                            b.getNombreArchivo());
                }
            }
        }
        return adjuntos;
    }

    // CONSTRUCTOR PRINCIPAL DEL REPORTE DIARIO AL STAFF
    // Diseñado siguiendo fielmente las referencias de IUS Tech y Lupa Control
    private String construirCuerpoReporteDiarioStaff(
            String destinatarioNombre,
            List<ActuacionJudicialRegistrada> actuaciones,
            List<BoletinJudicialCache> boletines,
            String descripcionIntro) {

        Set<Long> casosConNovedades = actuaciones.stream().map(a -> a.getCaso().getId()).collect(Collectors.toSet());
        for (BoletinJudicialCache b : boletines) {
            if (b.getCaso() != null) casosConNovedades.add(b.getCaso().getId());
        }

        // Mapeo de boletines por caso para enlazar en la tabla
        Map<Long, List<BoletinJudicialCache>> boletinesPorCaso = new HashMap<>();
        for (BoletinJudicialCache b : boletines) {
            if (b.getCaso() != null) {
                boletinesPorCaso.computeIfAbsent(b.getCaso().getId(), k -> new ArrayList<>()).add(b);
            }
        }

        StringBuilder html = new StringBuilder();

        // 1. Tarjeta de resumen ejecutivo / KPIs
        html.append("""
                <div style="background-color:#ffffff;border:1px solid #e2e8f0;border-radius:10px;padding:20px;margin-bottom:24px;box-shadow:0 1px 3px rgba(0,0,0,0.05);">
                  <div style="display:flex;justify-content:space-between;align-items:center;border-bottom:1px solid #f1f5f9;padding-bottom:12px;margin-bottom:16px;">
                    <div>
                      <span style="font-size:12px;text-transform:uppercase;letter-spacing:1px;color:#94a3b8;font-weight:bold;">Vigilancia Judicial Inteligente</span>
                      <h2 style="margin:4px 0 0;font-size:20px;color:#0f172a;font-family:'Lexend',sans-serif;">Reporte Diario de Movimientos Procesales</h2>
                    </div>
                    <div style="text-align:right;">
                      <span style="background-color:#fef3c7;color:#92400e;font-size:12px;font-weight:bold;padding:4px 10px;border-radius:20px;border:1px solid #fde68a;">
                        %s
                      </span>
                    </div>
                  </div>
                  <p style="margin:0 0 16px;color:#475569;font-size:14px;line-height:1.5;">Estimado(a) <strong>%s</strong>, %s</p>
                  
                  <table style="width:100%%;border-collapse:separate;border-spacing:8px 0;margin:0 0 8px;">
                    <tr>
                      <td style="background-color:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;padding:12px 16px;text-align:center;">
                        <span style="display:block;font-size:24px;font-weight:bold;color:#0284c7;">%d</span>
                        <span style="font-size:12px;color:#64748b;text-transform:uppercase;font-weight:600;">Casos con Novedades</span>
                      </td>
                      <td style="background-color:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;padding:12px 16px;text-align:center;">
                        <span style="display:block;font-size:24px;font-weight:bold;color:#16a34a;">%d</span>
                        <span style="font-size:12px;color:#64748b;text-transform:uppercase;font-weight:600;">Movimientos Nuevos</span>
                      </td>
                      <td style="background-color:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;padding:12px 16px;text-align:center;">
                        <span style="display:block;font-size:24px;font-weight:bold;color:#d97706;">%d</span>
                        <span style="font-size:12px;color:#64748b;text-transform:uppercase;font-weight:600;">Boletines / Providencias</span>
                      </td>
                    </tr>
                  </table>
                </div>
                """.formatted(
                LocalDate.now().format(DateTimeFormatter.ofPattern("d 'de' MMMM, yyyy", new Locale("es", "CO"))),
                escaparHtml(destinatarioNombre),
                escaparHtml(descripcionIntro),
                casosConNovedades.size(),
                actuaciones.size(),
                boletines.size()
        ));

        // 2. Tabla Principal de Movimientos Procesales (Estilo IUS Tech + Lupa Control)
        if (!actuaciones.isEmpty()) {
            html.append("""
                    <div style="margin-bottom:28px;">
                      <h3 style="margin:0 0 12px;font-size:16px;color:#0f172a;display:flex;align-items:center;gap:8px;">
                        <span style="background-color:#0284c7;width:8px;height:16px;border-radius:2px;display:inline-block;"></span>
                        Actuaciones Despachos Judiciales — Novedades (%d)
                      </h3>
                      
                      <div style="overflow-x:auto;border:1px solid #e2e8f0;border-radius:8px;background-color:#ffffff;">
                        <table style="width:100%%;border-collapse:collapse;font-size:13px;text-align:left;">
                          <thead>
                            <tr style="background-color:#0f172a;color:#ffffff;font-size:12px;text-transform:uppercase;letter-spacing:0.5px;">
                              <th style="padding:12px 14px;border-bottom:2px solid #D9A925;white-space:nowrap;">Radicado / Despacho</th>
                              <th style="padding:12px 14px;border-bottom:2px solid #D9A925;white-space:nowrap;">Partes / Responsable</th>
                              <th style="padding:12px 14px;border-bottom:2px solid #D9A925;white-space:nowrap;">Fecha Providencia</th>
                              <th style="padding:12px 14px;border-bottom:2px solid #D9A925;">Asunto / Actuación</th>
                              <th style="padding:12px 14px;border-bottom:2px solid #D9A925;min-width:280px;">Anotación Judicial</th>
                              <th style="padding:12px 14px;border-bottom:2px solid #D9A925;text-align:center;">Documento</th>
                            </tr>
                          </thead>
                          <tbody>
                    """.formatted(actuaciones.size()));

            boolean alterno = false;
            for (ActuacionJudicialRegistrada act : actuaciones) {
                Caso caso = act.getCaso();
                String fondo = alterno ? "#f8fafc" : "#ffffff";
                alterno = !alterno;

                String radicadoLegible = RadicadoUtil.formatearLegible(caso.getRadicadoId());
                String enlaceConsulta = emailService.enlaceConsultaDirecto(caso.getRadicadoId());
                String despacho = caso.getDespachoJudicial() != null ? caso.getDespachoJudicial() : "Despacho no informado";

                // Sujetos procesales / Partes
                String partes = caso.getSujetosProcesales() != null && !caso.getSujetosProcesales().isBlank()
                        ? caso.getSujetosProcesales().replace(" | ", "<br>")
                        : (caso.getCliente() != null ? "Cliente: " + caso.getCliente().getNombre() : "Partes reservadas");

                String responsable = caso.getAbogadoAsignado() != null
                        ? caso.getAbogadoAsignado().getNombre()
                        : "Sin abogado asignado";

                // Fechas
                LocalDateTime fFecha = act.getFechaActuacion() != null ? act.getFechaActuacion() : act.getFechaRegistro();
                String fechaStr = fFecha != null ? fFecha.format(FECHA_CORTO) : "Reciente";

                // Badges y alertas
                ActuacionRegistradaResponse resp = ActuacionRegistradaResponse.desde(act);
                StringBuilder badges = new StringBuilder();
                if (resp.esFundamental()) {
                    badges.append("<span style=\"background-color:#fee2e2;color:#b91c1c;font-size:10px;font-weight:bold;padding:2px 6px;border-radius:4px;display:inline-block;margin-top:4px;\">De Fondo</span> ");
                }
                if (resp.posibleAcreedorSubrogatorio()) {
                    badges.append("<span style=\"background-color:#fef2f2;color:#dc2626;font-size:10px;font-weight:bold;padding:2px 6px;border-radius:4px;border:1px solid #fecaca;display:inline-block;margin-top:4px;\">Acreedor Subrogatorio</span> ");
                }
                if (resp.posibleMedidaCautelar()) {
                    badges.append("<span style=\"background-color:#fef2f2;color:#dc2626;font-size:10px;font-weight:bold;padding:2px 6px;border-radius:4px;border:1px solid #fecaca;display:inline-block;margin-top:4px;\">Medida Cautelar</span> ");
                }
                if (resp.posibleLiquidacionCostas()) {
                    badges.append("<span style=\"background-color:#fef2f2;color:#dc2626;font-size:10px;font-weight:bold;padding:2px 6px;border-radius:4px;border:1px solid #fecaca;display:inline-block;margin-top:4px;\">Liq. Costas</span> ");
                }
                if (resp.fechaVencimientoTermino() != null) {
                    badges.append("<span style=\"background-color:#fef3c7;color:#b45309;font-size:10px;font-weight:bold;padding:2px 6px;border-radius:4px;border:1px solid #fde68a;display:inline-block;margin-top:4px;\">Vence: %s</span> ".formatted(resp.fechaVencimientoTermino()));
                }

                // Boletines / Documentos para esta fila
                List<BoletinJudicialCache> bolsCaso = boletinesPorCaso.getOrDefault(caso.getId(), List.of());
                StringBuilder celdaDoc = new StringBuilder();
                if (!bolsCaso.isEmpty()) {
                    BoletinJudicialCache primerBol = bolsCaso.get(0);
                    celdaDoc.append("""
                            <a href="%s" style="background-color:#D9A925;color:#ffffff;font-size:11px;font-weight:bold;padding:5px 9px;border-radius:4px;text-decoration:none;display:inline-block;white-space:nowrap;">
                              Ver Providencia &darr;
                            </a>
                            """.formatted(primerBol.getUrlOrigen()));
                } else if (act.isConDocumentos()) {
                    celdaDoc.append("<span style=\"color:#64748b;font-size:11px;\">En Rama Judicial</span>");
                } else {
                    celdaDoc.append("<span style=\"color:#cbd5e1;font-size:11px;\">—</span>");
                }

                html.append("""
                        <tr style="background-color:%s;border-bottom:1px solid #e2e8f0;vertical-align:top;">
                          <td style="padding:12px 14px;">
                            <a href="%s" style="font-family:monospace;font-size:12px;font-weight:bold;color:#0284c7;text-decoration:none;">%s</a>
                            <div style="font-size:11px;color:#64748b;margin-top:3px;max-width:240px;line-height:1.3;">%s</div>
                          </td>
                          <td style="padding:12px 14px;">
                            <div style="font-size:12px;color:#1e293b;max-width:220px;line-height:1.3;">%s</div>
                            <div style="font-size:11px;color:#94a3b8;margin-top:4px;"><strong>Resp:</strong> %s</div>
                          </td>
                          <td style="padding:12px 14px;white-space:nowrap;font-size:12px;color:#475569;font-weight:500;">
                            %s
                          </td>
                          <td style="padding:12px 14px;">
                            <div style="font-weight:bold;color:#0f172a;font-size:13px;">%s</div>
                            <div>%s</div>
                          </td>
                          <td style="padding:12px 14px;color:#334155;font-size:12px;line-height:1.4;">
                            %s
                          </td>
                          <td style="padding:12px 14px;text-align:center;">
                            %s
                          </td>
                        </tr>
                        """.formatted(
                        fondo,
                        enlaceConsulta,
                        escaparHtml(radicadoLegible),
                        escaparHtml(despacho),
                        partes,
                        escaparHtml(responsable),
                        escaparHtml(fechaStr),
                        escaparHtml(act.getNombreActuacion()),
                        badges,
                        act.getAnotacion() != null && !act.getAnotacion().isBlank() ? escaparHtml(act.getAnotacion()) : "<em style=\"color:#94a3b8;\">Sin anotación registrada</em>",
                        celdaDoc
                ));
            }

            html.append("""
                          </tbody>
                        </table>
                      </div>
                    </div>
                    """);
        }

        // 3. Sección de Boletines / Providencias oficiales del mes
        if (!boletines.isEmpty()) {
            html.append("""
                    <div style="background-color:#ffffff;border:1px solid #e2e8f0;border-radius:8px;padding:18px 20px;margin-bottom:24px;">
                      <h3 style="margin:0 0 12px;font-size:15px;color:#0f172a;display:flex;align-items:center;gap:8px;">
                        <span style="background-color:#D9A925;width:8px;height:16px;border-radius:2px;display:inline-block;"></span>
                        Boletines y Providencias Judiciales Disponibles (%d)
                      </h3>
                      <table style="width:100%%;border-collapse:collapse;font-size:12px;">
                        <thead>
                          <tr style="background-color:#f1f5f9;color:#475569;text-transform:uppercase;font-size:11px;">
                            <th style="padding:8px 12px;text-align:left;">Expediente</th>
                            <th style="padding:8px 12px;text-align:left;">Documento Oficial</th>
                            <th style="padding:8px 12px;text-align:left;">Fecha Descarga</th>
                            <th style="padding:8px 12px;text-align:center;">Acción</th>
                          </tr>
                        </thead>
                        <tbody>
                    """.formatted(boletines.size()));

            for (BoletinJudicialCache b : boletines) {
                Caso caso = b.getCaso();
                String rad = caso != null ? RadicadoUtil.formatearLegible(caso.getRadicadoId()) : "Sin radicado";
                String fDesc = b.getFechaDescarga() != null ? b.getFechaDescarga().format(FECHA_CORTO) : "Reciente";

                html.append("""
                        <tr style="border-bottom:1px solid #f1f5f9;">
                          <td style="padding:10px 12px;font-family:monospace;font-weight:bold;color:#0f172a;">%s</td>
                          <td style="padding:10px 12px;font-weight:600;color:#0284c7;">%s</td>
                          <td style="padding:10px 12px;color:#64748b;">%s</td>
                          <td style="padding:10px 12px;text-align:center;">
                            <a href="%s" style="color:#D9A925;font-weight:bold;text-decoration:underline;">Descargar PDF &rarr;</a>
                          </td>
                        </tr>
                        """.formatted(escaparHtml(rad), escaparHtml(b.getNombreArchivo()), escaparHtml(fDesc), b.getUrlOrigen()));
            }

            html.append("""
                        </tbody>
                      </table>
                    </div>
                    """);
        }

        // 4. Footer con advertencia de seguridad legal
        html.append("""
                <div style="font-size:11px;color:#94a3b8;border-top:1px solid #e2e8f0;padding-top:16px;line-height:1.4;">
                  Este informe es emitido por el sistema automatizado de <strong>Vigilancia Judicial Inteligente de SIE Jurídicos</strong>.
                  La información contenida proviene directamente de las fuentes oficiales de la Rama Judicial de Colombia y Publicaciones Procesales.
                  Para soporte o asignación de casos, contacte a la gerencia jurídica.
                </div>
                """);

        return html.toString();
    }

    // Formato cliente semanal (se mantiene intacto para clientes individuales)
    private String construirCuerpoDigestCliente(Caso caso, String nombreMostrado, List<BoletinJudicialCache> boletines, String introduccion) {
        String enlace = emailService.enlaceConsultaDirecto(caso.getRadicadoId());
        String radicadoLegible = RadicadoUtil.formatearLegible(caso.getRadicadoId());

        StringBuilder docs = new StringBuilder();
        for (BoletinJudicialCache boletin : boletines) {
            ActuacionJudicialRegistrada actuacion = boletin.getActuacion();
            String descripcion = actuacion != null
                    ? ActuacionRegistradaResponse.desde(actuacion).nombreActuacion()
                    : "Documento oficial del despacho";
            docs.append("""
                    <li style="margin-bottom:6px;"><strong>%s</strong><br>
                    <span style="font-size:12px;color:#64748b;">%s</span></li>
                    """.formatted(escaparHtml(boletin.getNombreArchivo()), escaparHtml(descripcion)));
        }

        return """
                <p style="margin:0 0 16px;font-size:15px;color:#334155;">%s</p>
                <div style="background-color:#ffffff;border:1px solid #e2e8f0;border-radius:8px;padding:20px;margin-bottom:20px;">
                  <p style="margin:0 0 4px;font-size:16px;font-weight:bold;color:#0f172a;">%s</p>
                  <p style="margin:0 0 16px;font-family:monospace;font-size:14px;color:#D9A925;font-weight:bold;">%s</p>
                  <p style="margin:0 0 8px;font-weight:bold;font-size:13px;color:#0f172a;text-transform:uppercase;">Documentos y Novedades de la Semana:</p>
                  <ul style="margin:0 0 16px;padding-left:20px;color:#334155;">%s</ul>
                  <p style="margin:0;">
                    <a href="%s" style="background-color:#D9A925;color:#ffffff;padding:8px 16px;border-radius:6px;text-decoration:none;font-weight:bold;display:inline-block;">
                      Consultar estado de mi proceso &rarr;
                    </a>
                  </p>
                </div>
                """.formatted(introduccion, escaparHtml(nombreMostrado),
                escaparHtml(radicadoLegible), docs, enlace);
    }

    private static String escaparHtml(String texto) {
        return texto == null ? "" : texto
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
