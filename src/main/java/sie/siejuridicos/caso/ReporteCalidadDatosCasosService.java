package sie.siejuridicos.caso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sie.siejuridicos.correo.EmailService;

import java.util.List;

// Pedido explícito del usuario (2026-11-09): "implementa una funcionalidad para todos los
// lunes de la semana a las 8 am que le mande a la gerencia un reporte de casos con fallos,
// radicados no correctos o invalidos, correos y numeros de telefono fallidos o que
// simplemente no han tenido reportes correctos en las ramas, esto para llevar una mayor
// integridad entre el google sheet y el sistema" -- ver ReporteCalidadDatosCasosScheduler
// (el "cuándo") y CasoRepository (las 4 consultas que alimentan esto, con el mismo filtro
// de privacidad ya usado en el resto del sistema: nunca un caso esPrivadoCifrado=true).
@Service
public class ReporteCalidadDatosCasosService {

    private static final Logger log = LoggerFactory.getLogger(ReporteCalidadDatosCasosService.class);

    private final CasoRepository casoRepository;
    private final EmailService emailService;
    private final String correoGerencia;

    public ReporteCalidadDatosCasosService(CasoRepository casoRepository,
                                            EmailService emailService,
                                            @Value("${app.correo.gerencia}") String correoGerencia) {
        this.casoRepository = casoRepository;
        this.emailService = emailService;
        this.correoGerencia = correoGerencia;
    }

    @Transactional(readOnly = true)
    public void generarYEnviar() {
        List<Caso> radicadosInvalidos = casoRepository.listarConRadicadoInvalidoParaReporte();
        List<Caso> sinCorreo = casoRepository.listarSinCorreoClienteParaReporte();
        List<Caso> sinTelefono = casoRepository.listarSinTelefonoClienteParaReporte();
        List<Caso> sinProceso = casoRepository.listarSinProcesoIdentificadoParaReporte();

        if (radicadosInvalidos.isEmpty() && sinCorreo.isEmpty() && sinTelefono.isEmpty() && sinProceso.isEmpty()) {
            log.info("Reporte semanal de calidad de datos: sin novedades, todos los casos activos tienen datos completos.");
            return;
        }

        // Un mismo caso puede aparecer en más de una categoría (ej. sin correo Y sin
        // teléfono) -- totalCasosConProblema cuenta casos ÚNICOS, no filas de tabla, para que
        // el asunto/registro no exagere el tamaño real del problema.
        java.util.Set<Long> idsUnicos = new java.util.HashSet<>();
        radicadosInvalidos.forEach(c -> idsUnicos.add(c.getId()));
        sinCorreo.forEach(c -> idsUnicos.add(c.getId()));
        sinTelefono.forEach(c -> idsUnicos.add(c.getId()));
        sinProceso.forEach(c -> idsUnicos.add(c.getId()));

        String cuerpo = construirCuerpo(radicadosInvalidos, sinCorreo, sinTelefono, sinProceso, idsUnicos.size());
        emailService.enviarReporteCalidadDatosGerencia(correoGerencia, cuerpo, idsUnicos.size());

        log.info("Reporte semanal de calidad de datos ENVIADO: {} caso(s) único(s) con algún problema "
                        + "({} radicado(s) inválido(s), {} sin correo, {} sin teléfono, {} sin proceso identificado).",
                idsUnicos.size(), radicadosInvalidos.size(), sinCorreo.size(), sinTelefono.size(), sinProceso.size());
    }

    private String construirCuerpo(List<Caso> radicadosInvalidos, List<Caso> sinCorreo, List<Caso> sinTelefono,
                                    List<Caso> sinProceso, int totalUnicos) {
        StringBuilder html = new StringBuilder();
        html.append("""
                <div style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#1e293b;line-height:1.5;">
                  <div style="text-align:center;padding:16px 0 24px;border-bottom:1px solid #e2e8f0;margin-bottom:24px;">
                    <h1 style="margin:0 0 6px;font-size:24px;color:#1e3a8a;font-weight:700;letter-spacing:-0.5px;">Calidad de Datos de Casos</h1>
                    <p style="margin:0;font-size:13px;color:#64748b;text-transform:uppercase;letter-spacing:1px;font-weight:600;">
                      SIE Jurídicos &middot; Reporte Semanal (Lunes)
                    </p>
                  </div>
                  <p style="margin:0 0 8px;font-size:14px;color:#334155;">Estimada Gerencia,</p>
                  <p style="margin:0 0 20px;font-size:13px;color:#475569;">
                    Este es el reporte semanal de integridad entre el Google Sheets de seguimiento de la firma y el sistema.
                    Se listan los casos activos que hoy tienen algún dato incompleto o un proceso que ningún motor de
                    vigilancia (CPNU ni SAMAI) ha logrado identificar todavía, para que puedan corregirse en la fuente
                    (la hoja de cálculo) cuanto antes. Total de casos únicos con al menos un problema: <strong>%d</strong>.
                  </p>
                """.formatted(totalUnicos));

        html.append(construirSeccion("Radicados con formato inválido",
                "El radicado capturado en la hoja no tiene la forma de un radicado judicial real (23 dígitos) -- "
                        + "revisar la columna de radicado de la fila correspondiente.",
                radicadosInvalidos, true));
        html.append(construirSeccion("Clientes sin correo electrónico",
                "No se puede enviar ninguna notificación (código de caso, reporte periódico, boletines) hasta que "
                        + "la hoja traiga un correo real para este cliente.",
                sinCorreo, false));
        html.append(construirSeccion("Clientes sin número de teléfono",
                "No se puede enviar WhatsApp (confirmaciones, recordatorios, boletines) hasta que la hoja traiga un "
                        + "teléfono real para este cliente.",
                sinTelefono, false));
        html.append(construirSeccion("Sin proceso identificado en ninguna rama",
                "El radicado tiene formato válido, pero ni el CPNU (Rama Judicial ordinaria) ni SAMAI (Consejo de "
                        + "Estado) han logrado ubicar el proceso real -- este caso no recibe seguimiento judicial "
                        + "automático de ningún tipo hasta que se resuelva manualmente.",
                sinProceso, false));

        html.append("""
                  <p style="margin:24px 0 0;">Atentamente,<br><strong>Sistema SIE Jurídicos</strong></p>
                </div>
                """);
        return html.toString();
    }

    private String construirSeccion(String titulo, String explicacion, List<Caso> casos, boolean mostrarRadicado) {
        if (casos.isEmpty()) {
            return "";
        }
        StringBuilder filas = new StringBuilder();
        for (Caso caso : casos) {
            String nombre = caso.getNombreEnHoja() != null && !caso.getNombreEnHoja().isBlank()
                    ? caso.getNombreEnHoja()
                    : (caso.getCliente() != null ? caso.getCliente().getNombre() : "Cliente sin nombre");
            String abogado = caso.getAbogadoAsignado() != null ? caso.getAbogadoAsignado().getNombre() : "Sin asignar";
            String numeroCaso = caso.getNumeroCaso() != null ? caso.getNumeroCaso() : "-";
            filas.append("""
                    <tr>
                      <td style="padding:8px 10px;border-bottom:1px solid #e2e8f0;font-size:12px;">%s</td>
                      <td style="padding:8px 10px;border-bottom:1px solid #e2e8f0;font-size:12px;">%s</td>
                      <td style="padding:8px 10px;border-bottom:1px solid #e2e8f0;font-size:12px;">%s</td>
                      <td style="padding:8px 10px;border-bottom:1px solid #e2e8f0;font-size:12px;font-family:monospace;">%s</td>
                      <td style="padding:8px 10px;border-bottom:1px solid #e2e8f0;font-size:12px;">%s</td>
                    </tr>
                    """.formatted(escaparHtml(caso.getFuente() != null ? caso.getFuente().getNombreVisible() : "-"),
                    escaparHtml(numeroCaso), escaparHtml(nombre),
                    mostrarRadicado && caso.getRadicadoId() != null ? escaparHtml(caso.getRadicadoId()) : "-",
                    escaparHtml(abogado)));
        }
        return """
                <div style="margin:0 0 24px;">
                  <p style="margin:0 0 4px;font-size:14px;font-weight:bold;color:#0f172a;">%s (%d)</p>
                  <p style="margin:0 0 10px;font-size:12px;color:#64748b;">%s</p>
                  <table style="width:100%%;border-collapse:collapse;background:#ffffff;border:1px solid #e2e8f0;border-radius:8px;overflow:hidden;">
                    <thead>
                      <tr style="background-color:#f8fafc;">
                        <th style="padding:8px 10px;text-align:left;font-size:11px;color:#64748b;text-transform:uppercase;">Fuente</th>
                        <th style="padding:8px 10px;text-align:left;font-size:11px;color:#64748b;text-transform:uppercase;">No. Caso</th>
                        <th style="padding:8px 10px;text-align:left;font-size:11px;color:#64748b;text-transform:uppercase;">Cliente</th>
                        <th style="padding:8px 10px;text-align:left;font-size:11px;color:#64748b;text-transform:uppercase;">Radicado</th>
                        <th style="padding:8px 10px;text-align:left;font-size:11px;color:#64748b;text-transform:uppercase;">Abogado asignado</th>
                      </tr>
                    </thead>
                    <tbody>%s</tbody>
                  </table>
                </div>
                """.formatted(escaparHtml(titulo), casos.size(), escaparHtml(explicacion), filas);
    }

    private static String escaparHtml(String texto) {
        return texto == null ? "" : texto
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
