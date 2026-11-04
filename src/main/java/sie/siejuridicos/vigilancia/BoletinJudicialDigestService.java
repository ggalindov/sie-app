package sie.siejuridicos.vigilancia;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sie.siejuridicos.caso.Caso;
import sie.siejuridicos.caso.RadicadoUtil;
import sie.siejuridicos.correo.EmailService;
import sie.siejuridicos.usuario.UsuarioInterno;
import sie.siejuridicos.usuario.UsuarioInternoRepository;
import sie.siejuridicos.vigilancia.dto.ActuacionRegistradaResponse;

import java.io.ByteArrayOutputStream;
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

        String cuerpo = construirCuerpoReporteDiarioStaff(
                "Gerencia General",
                actuacionesPendientes,
                boletinesPendientes,
                "Resumen consolidado de todos los movimientos procesales y boletines oficiales registrados en las últimas 24 horas para los expedientes bajo vigilancia de la firma."
        );

        List<EmailService.Adjunto> adjuntos = adjuntosSegurosDe(boletinesPendientes);
        byte[] excelBytes = generarExcelMovimientos(actuacionesPendientes);
        if (excelBytes.length > 0) {
            adjuntos.add(0, new EmailService.Adjunto("Reporte de Movimientos.xlsx", excelBytes));
        }

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
            byte[] excelBytes = generarExcelMovimientos(actuacionesAbogado);
            if (excelBytes.length > 0) {
                adjuntos.add(0, new EmailService.Adjunto("Reporte de Movimientos.xlsx", excelBytes));
            }

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
    @Transactional(readOnly = true)
    public boolean probarReporteDiario(String correoDestino) {
        List<ActuacionJudicialRegistrada> actuaciones =
                actuacionRepository.listarRecientesConCaso(PageRequest.of(0, 15));
        List<BoletinJudicialCache> boletines =
                boletinRepository.listarTodosConCaso().stream().limit(5).toList();

        if (actuaciones.isEmpty() && boletines.isEmpty()) {
            return false;
        }

        String cuerpo = construirCuerpoReporteDiarioStaff(
                "Gerencia General (Demostración)",
                actuaciones,
                boletines,
                "Demostración del reporte diario de vigilancia judicial inteligente según formato estándar de la firma con archivo Excel adjunto."
        );

        List<EmailService.Adjunto> adjuntos = adjuntosSegurosDe(boletines);
        byte[] excelBytes = generarExcelMovimientos(actuaciones);
        if (excelBytes.length > 0) {
            adjuntos.add(0, new EmailService.Adjunto("Reporte de Movimientos.xlsx", excelBytes));
        }

        emailService.enviarDigestBoletinGerente(correoDestino, cuerpo, adjuntos);
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

    // Genera el archivo Excel oficial 'Reporte de Movimientos.xlsx' idéntico a las columnas de IUS Tech
    public byte[] generarExcelMovimientos(List<ActuacionJudicialRegistrada> actuaciones) {
        if (actuaciones == null || actuaciones.isEmpty()) {
            return new byte[0];
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XSSFSheet sheet = workbook.createSheet("Reporte de Movimientos");

            // Estilo cabecera
            XSSFCellStyle headerStyle = workbook.createCellStyle();
            XSSFFont headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 30, (byte) 58, (byte) 138}, null)); // Navy #1e3a8a
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            // Estilo celdas normales
            XSSFCellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);

            Row headerRow = sheet.createRow(0);
            String[] columns = {"Radicado", "Despacho", "Demandante", "Demandado", "FechaProvidencia", "Asunto", "Anotación", "Responsable"};
            for (int i = 0; i < columns.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (ActuacionJudicialRegistrada act : actuaciones) {
                Row row = sheet.createRow(rowIdx++);
                Caso c = act.getCaso();
                String radicado = c != null && c.getRadicadoId() != null ? c.getRadicadoId() : "";
                String despacho = c != null && c.getDespachoJudicial() != null ? c.getDespachoJudicial() : "";
                String[] partes = extraerDemandanteYDemandado(c);
                String demandante = partes[0];
                String demandado = partes[1];
                LocalDateTime f = act.getFechaActuacion() != null ? act.getFechaActuacion() : act.getFechaRegistro();
                String fechaProv = f != null ? f.format(FECHA_CORTO) : "";
                String asunto = act.getNombreActuacion() != null ? act.getNombreActuacion() : "";
                String anotacion = act.getAnotacion() != null ? act.getAnotacion() : "";
                String responsable = c != null && c.getAbogadoAsignado() != null ? c.getAbogadoAsignado().getNombre() : "DRA TATIANA BUSTOS";

                Cell c0 = row.createCell(0); c0.setCellValue(radicado); c0.setCellStyle(dataStyle);
                Cell c1 = row.createCell(1); c1.setCellValue(despacho); c1.setCellStyle(dataStyle);
                Cell c2 = row.createCell(2); c2.setCellValue(demandante); c2.setCellStyle(dataStyle);
                Cell c3 = row.createCell(3); c3.setCellValue(demandado); c3.setCellStyle(dataStyle);
                Cell c4 = row.createCell(4); c4.setCellValue(fechaProv); c4.setCellStyle(dataStyle);
                Cell c5 = row.createCell(5); c5.setCellValue(asunto); c5.setCellStyle(dataStyle);
                Cell c6 = row.createCell(6); c6.setCellValue(anotacion); c6.setCellStyle(dataStyle);
                Cell c7 = row.createCell(7); c7.setCellValue(responsable); c7.setCellStyle(dataStyle);
            }

            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Error generando Excel de movimientos procesales", e);
            return new byte[0];
        }
    }

    // Extrae ordenadamente demandante y demandado de los sujetos procesales de la Rama Judicial
    private static String[] extraerDemandanteYDemandado(Caso caso) {
        if (caso == null) return new String[]{"", ""};
        String sujetos = caso.getSujetosProcesales();
        if (sujetos == null || sujetos.isBlank()) {
            String cli = caso.getCliente() != null ? caso.getCliente().getNombre() : "";
            return new String[]{cli, "Contraparte según proceso"};
        }
        String demandante = "";
        String demandado = "";
        String[] tokens = sujetos.split("\\|");
        for (String token : tokens) {
            String t = token.trim();
            String tLower = t.toLowerCase();
            if (tLower.startsWith("demandante") || tLower.startsWith("accionante") || tLower.startsWith("convocante")) {
                int idx = t.indexOf(':');
                demandante = idx >= 0 ? t.substring(idx + 1).trim() : t;
            } else if (tLower.startsWith("demandado") || tLower.startsWith("accionado") || tLower.startsWith("convocado") || tLower.startsWith("indiciado")) {
                int idx = t.indexOf(':');
                demandado = idx >= 0 ? t.substring(idx + 1).trim() : t;
            }
        }
        if (demandante.isBlank() && caso.getCliente() != null) {
            demandante = caso.getCliente().getNombre();
        }
        return new String[]{demandante, demandado};
    }

    // CONSTRUCTOR PRINCIPAL DEL REPORTE DIARIO AL STAFF
    // Diseñado siguiendo exactamente la estructura y columnas de IUS Tech y Lupa Control (Radicado, Despacho, Demandante, Demandado, FechaProvidencia, Asunto, Anotación, Responsable)
    private String construirCuerpoReporteDiarioStaff(
            String destinatarioNombre,
            List<ActuacionJudicialRegistrada> actuaciones,
            List<BoletinJudicialCache> boletines,
            String descripcionIntro) {

        Set<Long> casosConNovedades = actuaciones.stream().map(a -> a.getCaso().getId()).collect(Collectors.toSet());
        for (BoletinJudicialCache b : boletines) {
            if (b.getCaso() != null) casosConNovedades.add(b.getCaso().getId());
        }

        StringBuilder html = new StringBuilder();

        // 1. Encabezado de Marca y Título Central estilo IUS Tech
        html.append("""
                <div style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#1e293b;line-height:1.5;">
                  <div style="text-align:center;padding:16px 0 24px;border-bottom:1px solid #e2e8f0;margin-bottom:24px;">
                    <h1 style="margin:0 0 6px;font-size:24px;color:#1e3a8a;font-weight:700;letter-spacing:-0.5px;">Vigilancia Judicial Inteligente</h1>
                    <p style="margin:0;font-size:13px;color:#64748b;text-transform:uppercase;letter-spacing:1px;font-weight:600;">
                      SIE Jurídicos &middot; Reporte Oficial de Movimientos Procesales
                    </p>
                  </div>

                  <p style="margin:0 0 8px;font-size:14px;color:#334155;">Estimado(a) <strong>%s</strong>,</p>
                  <p style="margin:0 0 20px;font-size:13px;color:#475569;">
                    Le informamos que se han registrado <strong>nuevos movimientos procesales</strong> en los expedientes bajo nuestra vigilancia, detallados a continuación y adjuntos en la hoja de cálculo <em>Reporte de Movimientos.xlsx</em>.
                  </p>
                """.formatted(escaparHtml(destinatarioNombre)));

        // 2. Tarjeta Resumen de Indicadores
        html.append("""
                  <table style="width:100%%;border-collapse:separate;border-spacing:10px 0;margin:0 0 24px;">
                    <tr>
                      <td style="background-color:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;padding:12px 16px;text-align:center;">
                        <span style="display:block;font-size:22px;font-weight:bold;color:#1e3a8a;">%d</span>
                        <span style="font-size:11px;color:#64748b;text-transform:uppercase;font-weight:600;">Expedientes</span>
                      </td>
                      <td style="background-color:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;padding:12px 16px;text-align:center;">
                        <span style="display:block;font-size:22px;font-weight:bold;color:#16a34a;">%d</span>
                        <span style="font-size:11px;color:#64748b;text-transform:uppercase;font-weight:600;">Movimientos Nuevos</span>
                      </td>
                      <td style="background-color:#f8fafc;border:1px solid #e2e8f0;border-radius:8px;padding:12px 16px;text-align:center;">
                        <span style="display:block;font-size:22px;font-weight:bold;color:#d97706;">%d</span>
                        <span style="font-size:11px;color:#64748b;text-transform:uppercase;font-weight:600;">Providencias / Estados</span>
                      </td>
                    </tr>
                  </table>
                """.formatted(casosConNovedades.size(), actuaciones.size(), boletines.size()));

        // 3. Tabla Principal de Movimientos Procesales (8 Columnas exactas de IUS Tech)
        if (!actuaciones.isEmpty()) {
            html.append("""
                  <div style="overflow-x:auto;border:1px solid #e2e8f0;border-radius:8px;background-color:#ffffff;margin-bottom:28px;">
                    <table style="width:100%%;border-collapse:collapse;font-size:12px;text-align:left;">
                      <thead>
                        <tr style="background-color:#f8fafc;color:#1e293b;font-size:11px;font-weight:700;border-bottom:2px solid #cbd5e1;">
                          <th style="padding:10px 12px;border:1px solid #e2e8f0;white-space:nowrap;">Radicado</th>
                          <th style="padding:10px 12px;border:1px solid #e2e8f0;white-space:nowrap;">Despacho</th>
                          <th style="padding:10px 12px;border:1px solid #e2e8f0;white-space:nowrap;">Demandante</th>
                          <th style="padding:10px 12px;border:1px solid #e2e8f0;white-space:nowrap;">Demandado</th>
                          <th style="padding:10px 12px;border:1px solid #e2e8f0;white-space:nowrap;">FechaProvidencia</th>
                          <th style="padding:10px 12px;border:1px solid #e2e8f0;white-space:nowrap;">Asunto</th>
                          <th style="padding:10px 12px;border:1px solid #e2e8f0;min-width:280px;">Anotación</th>
                          <th style="padding:10px 12px;border:1px solid #e2e8f0;white-space:nowrap;">Responsable</th>
                        </tr>
                      </thead>
                      <tbody>
                """);

            boolean alterno = false;
            for (ActuacionJudicialRegistrada act : actuaciones) {
                Caso caso = act.getCaso();
                String fondo = alterno ? "#f8fafc" : "#ffffff";
                alterno = !alterno;

                String radicado = caso != null && caso.getRadicadoId() != null ? caso.getRadicadoId() : "";
                String enlaceConsulta = caso != null && caso.getRadicadoId() != null ? emailService.enlaceConsultaDirecto(caso.getRadicadoId()) : "#";
                String despacho = caso != null && caso.getDespachoJudicial() != null ? caso.getDespachoJudicial().toUpperCase() : "JUZGADO NO INFORMADO";

                String[] partes = extraerDemandanteYDemandado(caso);
                String demandante = partes[0];
                String demandado = partes[1];

                String responsable = caso != null && caso.getAbogadoAsignado() != null
                        ? caso.getAbogadoAsignado().getNombre().toUpperCase()
                        : "DRA TATIANA BUSTOS";

                LocalDateTime fFecha = act.getFechaActuacion() != null ? act.getFechaActuacion() : act.getFechaRegistro();
                String fechaStr = fFecha != null ? fFecha.format(FECHA_CORTO) : LocalDate.now().format(FECHA_CORTO);

                // Badges discretos si es actuación de fondo o término
                ActuacionRegistradaResponse resp = ActuacionRegistradaResponse.desde(act);
                StringBuilder badgeHtml = new StringBuilder();
                if (resp.esFundamental()) {
                    badgeHtml.append("<br><span style=\"background-color:#fee2e2;color:#b91c1c;font-size:10px;font-weight:bold;padding:1px 5px;border-radius:3px;\">De Fondo</span>");
                }
                if (resp.posibleMedidaCautelar()) {
                    badgeHtml.append("<br><span style=\"background-color:#fef2f2;color:#dc2626;font-size:10px;font-weight:bold;padding:1px 5px;border-radius:3px;border:1px solid #fecaca;\">Medida Cautelar</span>");
                }
                if (resp.posibleAcreedorSubrogatorio()) {
                    badgeHtml.append("<br><span style=\"background-color:#fef2f2;color:#dc2626;font-size:10px;font-weight:bold;padding:1px 5px;border-radius:3px;border:1px solid #fecaca;\">Acreedor Subrogatorio</span>");
                }
                // Pedido explícito del usuario (2026-11-04, auditoría en vivo del correo real
                // de LUPA "Movimientos..."): su tabla trae una columna "TERMINACIÓN: NORMAL" y
                // alerta aparte cuando el abogado no asistió a una audiencia -- mismo criterio
                // que las badges de arriba, ver ClasificadorMovimientoJudicial.
                if ("AUDIENCIA_PROGRAMADA".equals(resp.tipoMovimiento())) {
                    badgeHtml.append("<br><span style=\"background-color:#eff6ff;color:#1d4ed8;font-size:10px;font-weight:bold;padding:1px 5px;border-radius:3px;border:1px solid #bfdbfe;\">Audiencia Programada</span>");
                }
                if ("TERMINACION_PROCESO".equals(resp.tipoMovimiento())) {
                    badgeHtml.append("<br><span style=\"background-color:#f0fdf4;color:#15803d;font-size:10px;font-weight:bold;padding:1px 5px;border-radius:3px;border:1px solid #bbf7d0;\">Terminación de Proceso</span>");
                }
                if ("NO_ASISTIO".equals(resp.asistenciaAudiencia())) {
                    badgeHtml.append("<br><span style=\"background-color:#dc2626;color:#ffffff;font-size:10px;font-weight:bold;padding:1px 5px;border-radius:3px;\">&#9888; Posible inasistencia a audiencia</span>");
                }

                String asunto = act.getNombreActuacion() != null ? act.getNombreActuacion().toUpperCase() : "ACTUACIÓN JUDICIAL";
                String anotacion = act.getAnotacion() != null && !act.getAnotacion().isBlank()
                        ? act.getAnotacion()
                        : "Actuación registrada en despacho judicial.";

                html.append("""
                        <tr style="background-color:%s;vertical-align:top;">
                          <td style="padding:10px 12px;border:1px solid #e2e8f0;font-family:monospace;font-size:11px;font-weight:600;white-space:nowrap;">
                            <a href="%s" style="color:#0284c7;text-decoration:none;">%s</a>
                          </td>
                          <td style="padding:10px 12px;border:1px solid #e2e8f0;font-size:11px;color:#334155;max-width:200px;line-height:1.3;">%s</td>
                          <td style="padding:10px 12px;border:1px solid #e2e8f0;font-size:11px;color:#334155;max-width:160px;">%s</td>
                          <td style="padding:10px 12px;border:1px solid #e2e8f0;font-size:11px;color:#334155;max-width:160px;">%s</td>
                          <td style="padding:10px 12px;border:1px solid #e2e8f0;font-size:11px;color:#475569;white-space:nowrap;">%s</td>
                          <td style="padding:10px 12px;border:1px solid #e2e8f0;font-size:11px;color:#0f172a;font-weight:600;">%s%s</td>
                          <td style="padding:10px 12px;border:1px solid #e2e8f0;font-size:11px;color:#334155;line-height:1.4;">%s</td>
                          <td style="padding:10px 12px;border:1px solid #e2e8f0;font-size:11px;color:#475569;white-space:nowrap;font-weight:600;">%s</td>
                        </tr>
                        """.formatted(
                        fondo,
                        enlaceConsulta,
                        escaparHtml(radicado),
                        escaparHtml(despacho),
                        escaparHtml(demandante),
                        escaparHtml(demandado),
                        escaparHtml(fechaStr),
                        escaparHtml(asunto),
                        badgeHtml,
                        escaparHtml(anotacion),
                        escaparHtml(responsable)
                ));
            }

            html.append("""
                      </tbody>
                    </table>
                  </div>
                """);
        }

        // 4. Sección de Boletines / Providencias oficiales descargables
        if (!boletines.isEmpty()) {
            html.append("""
                  <div style="background-color:#ffffff;border:1px solid #e2e8f0;border-radius:8px;padding:18px 20px;margin-bottom:24px;">
                    <h3 style="margin:0 0 12px;font-size:15px;color:#0f172a;display:flex;align-items:center;gap:8px;">
                      <span style="background-color:#D9A925;width:8px;height:16px;border-radius:2px;display:inline-block;"></span>
                      Providencias y Estados Electrónicos Descargables (%d)
                    </h3>
                    <table style="width:100%%;border-collapse:collapse;font-size:12px;">
                      <thead>
                        <tr style="background-color:#f1f5f9;color:#475569;text-transform:uppercase;font-size:11px;">
                          <th style="padding:8px 12px;text-align:left;">Radicado</th>
                          <th style="padding:8px 12px;text-align:left;">Documento Oficial</th>
                          <th style="padding:8px 12px;text-align:left;">Fecha Publicación</th>
                          <th style="padding:8px 12px;text-align:center;">Acción</th>
                        </tr>
                      </thead>
                      <tbody>
                  """.formatted(boletines.size()));

            for (BoletinJudicialCache b : boletines) {
                Caso caso = b.getCaso();
                String rad = caso != null ? caso.getRadicadoId() : "Sin radicado";
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

        // 5. Footer con advertencia de seguridad legal
        html.append("""
                  <div style="font-size:11px;color:#94a3b8;border-top:1px solid #e2e8f0;padding-top:16px;line-height:1.4;">
                    Este informe es emitido por el sistema automatizado de <strong>Vigilancia Judicial Inteligente de SIE Jurídicos</strong>.
                    La información contenida proviene directamente de las fuentes oficiales de la Rama Judicial de Colombia y Publicaciones Procesales.
                    Para soporte o asignación de casos, contacte a la gerencia jurídica.
                  </div>
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
