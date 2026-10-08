package sie.siejuridicos.chatbot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicCacheOptions;
import org.springframework.ai.anthropic.AnthropicCacheStrategy;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import sie.siejuridicos.chatbot.dto.MensajeChatbotRequest;
import sie.siejuridicos.chatbot.dto.MensajeChatbotResponse;
import sie.siejuridicos.chatbot.dto.TurnoChatDto;
import sie.siejuridicos.common.exception.ChatbotNoDisponibleException;
import sie.siejuridicos.common.exception.LimiteChatbotExcedidoException;
import sie.siejuridicos.common.exception.RecursoNoEncontradoException;
import sie.siejuridicos.faq.PreguntaFrecuenteService;

import java.util.ArrayList;
import java.util.List;

@Service
public class ChatbotService {

    private static final String PROMPT_SISTEMA = """
            Eres Siebot, el Consultor Jurídico Senior y Asesor de Venta y Orientación de SIE Jurídicos, \
            una de las firmas de abogados líderes de Bogotá, Colombia, con más de 20 años de trayectoria, \
            más de 800 casos resueltos con éxito y un equipo interdisciplinario de 8 abogados especialistas.

            ## Tu Misión Principal (Cierre de Ventas y Agendamiento de Asesorías)
            Tu objetivo prioritario e inmutable es ORIENTAR con autoridad técnica y CONVERTIR a cada consultante \
            en un cliente de la firma agendando una consulta o asesoría jurídica personalizada (presencial o virtual).
            No eres una enciclopedia legal abstracta ni un consultorio gratuito pasivo: eres el asesor comercial y \
            técnico que demuestra el valor irremplazable de contratar a los abogados de SIE Jurídicos para proteger su \
            patrimonio, libertad, familia o empresa.

            ## Estructura OBLIGATORIA de CADA una de tus Respuestas
            Toda respuesta que des debe ser ágil, persuasiva, completa y estructurada en exactamente 4 partes:

            1. **Empatía y Validación Inmediata (1 a 2 renglones)**:
               Demuestra que comprendes el problema con calidez, respaldo y seguridad ("Comprendo perfectamente la situación...", "En Colombia este es un asunto delicado pero con sólidas herramientas jurídicas a tu favor...").

            2. **Diagnóstico Legal Estratégico (2 a 4 viñetas concretas)**:
               Explica los derechos y requisitos esenciales según la legislación colombiana (CST, Código Civil, Código de la Infancia, Ley 1258 de SAS, SIC, etc.). Demuestra el alto nivel técnico de la firma, pero aclara que la estrategia exitosa depende del análisis minucioso de las pruebas documentales.

            3. **Alerta de Riesgo o Urgencia (1 a 2 renglones)**:
               Explica por qué no deben dejar pasar el tiempo: los términos judiciales corren, los derechos prescriben, o un error al presentar una solicitud, demanda o liquidación puede costar millones de pesos o anular el proceso.

            4. **Cierre de Venta y Llamado a la Acción Ineludible (OBLIGATORIO AL FINAL DE CADA MENSAJE)**:
               Invita activamente a agendar de inmediato la asesoría jurídica con los abogados de la firma.
               Pide siempre los datos para coordinarla:
               "¿Deseas que coordinemos tu asesoría personalizada con uno de nuestros abogados especialistas? Compárteme por favor tu **Nombre completo**, un **teléfono o WhatsApp** y tu **correo electrónico**, y nuestro equipo legal te contactará para fijar la cita."
               Recuérdale también que si tiene urgencia puede escribir directo al WhatsApp oficial: [+57 324 3668845](https://wa.me/573243668845).

            ## Conocimiento Jurídico Colombiano de la Firma
            - **Laboral y Seguridad Social**: Despidos sin justa causa (art. 64 CST), indemnización moratoria (art. 65 CST), liquidaciones de cesantías, primas y vacaciones, estabilidad laboral reforzada (fueros de salud/maternidad), acoso laboral (Ley 1010 de 2006).
            - **Familia**: Fijación, aumento y cobro ejecutivo de cuotas de alimentos (Código de Infancia y Adolescencia), divorcios (notaría o juzgado), custodia y visitas, liquidación de sociedad conyugal y sucesiones.
            - **Civil y Comercial**: Incumplimiento de contratos y promesas de compraventa, restitución de inmuebles arrendados, cobro de títulos valores (letras, pagarés, facturas), constitución de SAS (Ley 1258 de 2008) y registro de marcas ante la SIC.
            - **Acciones Urgentes**: Tutelas (fallo en 10 días para derechos fundamentales) y derechos de petición (Ley 1755 de 2015).

            ## Registro Inmediato de Prospectos (Herramienta `registrarSolicitud`)
            En cuanto el usuario te proporcione su nombre, su correo y/o su teléfono, DEBES invocar de inmediato la herramienta `registrarSolicitud` con esos datos y el motivo de su caso. Al invocarla, confírmale al usuario con entusiasmo que sus datos quedaron registrados en el sistema del despacho y que un abogado especialista lo contactará para la asesoría.

            ## Reglas Estrictas de Formato Visual y Redacción
            - NUNCA dejes una respuesta cortada a medio terminar. Completa todas tus ideas y el cierre de venta.
            - NUNCA uses encabezados de markdown tipo `##` o `###` en el chat; usa títulos en negrita como `**📄 Requisitos clave:**` o `**⚖️ Pasos recomendados:**`.
            - NUNCA dejes una viñeta `•` sola en un renglón. Escribe siempre la viñeta y su texto en la misma línea: `• **Título**: Explicación breve.`
            - Mantén párrafos cortos y aireados (máximo 2 a 3 líneas) para que sea un placer leerlo en celulares.
            - Usa emojis profesionales con sobriedad (⚖️, 📄, 💬, 📞, ✅).
            """;

    private static final Logger log = LoggerFactory.getLogger(ChatbotService.class);

    private static final String MENSAJE_NO_DISPONIBLE =
            "El asistente virtual no está disponible en este momento. Por favor contáctanos por WhatsApp "
                    + "o completa el formulario de contacto y un abogado te responderá a la brevedad.";

    private static final String MENSAJE_PRESUPUESTO_AGOTADO =
            "En este momento el asistente virtual alcanzó su límite de uso del mes. Escríbenos por "
                    + "WhatsApp (+57 324 3668845) y con gusto te ayudamos directamente.";

    // Fracción del presupuesto mensual a partir de la cual se reduce el tamaño de respuesta
    // (RF: "alternar consumo de tokens"), y fracción a partir de la cual se corta la llamada a
    // la API por completo y se remite a WhatsApp sin costo. 0.97 deja un colchón del 3% del
    // presupuesto (de sobra: incluso en el nivel "mínimo" una llamada cuesta centésimas de
    // centavo) para que el gasto real nunca sobrepase lo configurado.
    private static final double UMBRAL_REDUCIDO = 0.70;
    private static final double UMBRAL_MINIMO = 0.90;
    private static final double UMBRAL_CORTE = 0.97;

    // Prompt caching de Anthropic (reduce el costo de entrada del gobernador de presupuesto
    // de arriba). CONVERSATION_HISTORY, no SYSTEM_AND_TOOLS: el modelo configurado es Claude
    // Haiku 5.5, cuyo mínimo cacheable son 4096 tokens, y el prompt de sistema + la única
    // tool (registrarSolicitud) suman ~1700 tokens — CONVERSATION_HISTORY pone el breakpoint
    // al final del historial ya recibido, cacheando el prefijo completo en conversaciones de varias vueltas.
    private static final AnthropicCacheOptions OPCIONES_CACHE = AnthropicCacheOptions.builder()
            .strategy(AnthropicCacheStrategy.CONVERSATION_HISTORY)
            .build();

    private final ConversacionChatbotRepository conversacionChatbotRepository;
    private final ChatClient chatClient;
    private final ChatbotTools chatbotTools;
    private final PreguntaFrecuenteService preguntaFrecuenteService;
    private final String modelo;
    private final int limiteMensual;
    private final boolean apiKeyConfigurada;

    private final long presupuestoMensualMicroUsd;
    private final int maxTokensNormal;
    private final int maxTokensReducido;
    private final int maxTokensMinimo;
    private final long costoEntradaMicroUsdPorToken;
    private final long costoSalidaMicroUsdPorToken;

    public ChatbotService(ConversacionChatbotRepository conversacionChatbotRepository,
                           ChatClient.Builder chatClientBuilder,
                           ChatbotTools chatbotTools,
                           PreguntaFrecuenteService preguntaFrecuenteService,
                           @Value("${spring.ai.anthropic.chat.options.model}") String modelo,
                           @Value("${app.chatbot.limite-mensual}") int limiteMensual,
                           @Value("${spring.ai.anthropic.api-key:}") String apiKey,
                           @Value("${app.chatbot.presupuesto-mensual-micro-usd:15000000}") long presupuestoMensualMicroUsd,
                           @Value("${app.chatbot.max-tokens-normal:1200}") int maxTokensNormal,
                           @Value("${app.chatbot.max-tokens-reducido:800}") int maxTokensReducido,
                           @Value("${app.chatbot.max-tokens-minimo:500}") int maxTokensMinimo,
                           @Value("${app.chatbot.costo-entrada-micro-usd-por-token:1}") long costoEntradaMicroUsdPorToken,
                           @Value("${app.chatbot.costo-salida-micro-usd-por-token:5}") long costoSalidaMicroUsdPorToken) {
        this.conversacionChatbotRepository = conversacionChatbotRepository;
        this.chatClient = chatClientBuilder.defaultSystem(PROMPT_SISTEMA).build();
        this.chatbotTools = chatbotTools;
        this.preguntaFrecuenteService = preguntaFrecuenteService;
        this.modelo = modelo;
        this.limiteMensual = limiteMensual;
        this.apiKeyConfigurada = apiKey != null && !apiKey.isBlank();
        this.presupuestoMensualMicroUsd = presupuestoMensualMicroUsd;
        this.maxTokensNormal = maxTokensNormal;
        this.maxTokensReducido = maxTokensReducido;
        this.maxTokensMinimo = maxTokensMinimo;
        this.costoEntradaMicroUsdPorToken = costoEntradaMicroUsdPorToken;
        this.costoSalidaMicroUsdPorToken = costoSalidaMicroUsdPorToken;
        if (!this.apiKeyConfigurada) {
            log.warn("ANTHROPIC_API_KEY no está configurada: el chatbot responderá con un mensaje de "
                    + "indisponibilidad en vez de generar respuestas reales hasta que se configure.");
        }
        log.info("Chatbot configurado con modelo {}, presupuesto mensual {} USD, tope {} conversaciones/mes",
                modelo, presupuestoMensualMicroUsd / 1_000_000.0, limiteMensual);
    }

    // Sin @Transactional a nivel de método a propósito: la llamada a Claude puede tardar
    // varios segundos y no debe mantener abierta una transacción/conexión de base de datos
    // mientras espera esa respuesta de red. Cada operación de repositorio (findById, save,
    // el registro de costo, y el @Transactional propio de ChatbotTools.registrarSolicitud)
    // maneja su propia transacción corta de forma independiente.
    public MensajeChatbotResponse responder(MensajeChatbotRequest request) {
        // Sin key configurada, ni siquiera se intenta llamar a Claude (fallaría de todas
        // formas): se responde de inmediato con un mensaje claro en vez de esperar el
        // timeout de red. No se descuenta del tope mensual porque no es una conversación
        // real con el modelo.
        if (!apiKeyConfigurada) {
            throw new ChatbotNoDisponibleException(MENSAJE_NO_DISPONIBLE);
        }

        // Gobernador de presupuesto real: se revisa el gasto acumulado del mes ANTES de cada
        // mensaje (no solo al iniciar la conversación), porque una sola conversación larga
        // podría agotar el presupuesto por sí sola. Si ya se alcanzó el umbral de corte, ni
        // siquiera se llama a Anthropic (costo cero) y se remite directo a WhatsApp.
        long costoActualMicroUsd = conversacionChatbotRepository.obtenerCostoMensualChatbotMicroUsd();
        if (presupuestoMensualMicroUsd > 0 && costoActualMicroUsd >= Math.round(presupuestoMensualMicroUsd * UMBRAL_CORTE)) {
            log.warn("Presupuesto mensual del chatbot casi agotado ({}/{} micro-USD): se responde sin "
                    + "llamar a Anthropic.", costoActualMicroUsd, presupuestoMensualMicroUsd);
            throw new ChatbotNoDisponibleException(MENSAJE_PRESUPUESTO_AGOTADO);
        }
        int maxTokensNivel = calcularMaxTokens(costoActualMicroUsd);

        ConversacionChatbot conversacion = obtenerOCrearConversacion(request.conversacionId());

        List<Message> mensajes = construirHistorial(request.historial());
        mensajes.add(new UserMessage(request.mensaje()));

        ChatResponse chatResponse;
        try {
            chatResponse = chatClient.prompt()
                    .messages(mensajes)
                    .tools(chatbotTools)
                    .options(AnthropicChatOptions.builder()
                            .model(modelo)
                            .maxTokens(maxTokensNivel)
                            .thinkingDisabled()
                            .cacheOptions(OPCIONES_CACHE))
                    .call()
                    .chatResponse();
        } catch (RuntimeException ex) {
            // Cualquier falla al llamar a Anthropic (key inválida, red, cuota, timeout,
            // respuesta vacía) se traduce en un 503 claro para el frontend en vez de dejar
            // que el error crudo de la librería suba como un 500 sin contexto útil.
            log.error("Falló la llamada al modelo de IA del chatbot (conversación {}): {}",
                    conversacion.getId(), ex.getMessage(), ex);
            throw new ChatbotNoDisponibleException(MENSAJE_NO_DISPONIBLE);
        }

        String respuesta = chatResponse != null && chatResponse.getResult() != null
                ? chatResponse.getResult().getOutput().getText()
                : null;

        if (respuesta == null || respuesta.isBlank()) {
            log.warn("El modelo de IA devolvió una respuesta vacía para la conversación {}", conversacion.getId());
            throw new ChatbotNoDisponibleException(MENSAJE_NO_DISPONIBLE);
        }

        registrarCostoReal(chatResponse);

        // FAQ auto-alimentada: cada pregunta del usuario se cuenta (normalizada); cuando
        // se repite lo suficiente aparece como candidata en el panel para que un abogado
        // la apruebe con un clic. registrarPregunta nunca lanza excepción (ver
        // PreguntaFrecuenteService), así que no hace falta try/catch aquí.
        preguntaFrecuenteService.registrarPregunta(request.mensaje(), respuesta);

        conversacion.setCantidadMensajes(conversacion.getCantidadMensajes() + 1);
        conversacionChatbotRepository.save(conversacion);

        return new MensajeChatbotResponse(conversacion.getId(), respuesta);
    }

    // "Alterna el consumo de tokens" (RF pedido explícitamente): mientras hay presupuesto de
    // sobra, respuestas completas (nivel normal); acercándose al tope, respuestas cada vez más
    // cortas (menos tokens de salida = menos costo por llamada), para estirar el presupuesto
    // en vez de cortar de golpe. Solo se corta por completo al llegar a UMBRAL_CORTE.
    private int calcularMaxTokens(long costoActualMicroUsd) {
        if (presupuestoMensualMicroUsd <= 0) {
            return maxTokensNormal;
        }
        double proporcionUsada = (double) costoActualMicroUsd / presupuestoMensualMicroUsd;
        if (proporcionUsada >= UMBRAL_MINIMO) {
            return maxTokensMinimo;
        }
        if (proporcionUsada >= UMBRAL_REDUCIDO) {
            return maxTokensReducido;
        }
        return maxTokensNormal;
    }

    // Suma el costo real (no estimado) de esta llamada al acumulado del mes, a partir de los
    // tokens que Anthropic reportó de verdad en la respuesta. Un fallo aquí no debe tumbar la
    // respuesta al usuario: en el peor caso se pierde precisión en el contador de gasto, nunca
    // la conversación.
    private void registrarCostoReal(ChatResponse chatResponse) {
        if (chatResponse == null || chatResponse.getMetadata() == null) {
            return;
        }
        Usage usage = chatResponse.getMetadata().getUsage();
        if (usage == null) {
            return;
        }
        long tokensEntradaBase = usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
        long tokensSalida = usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;

        // Con OPCIONES_CACHE activo, Anthropic reporta los tokens de caché APARTE de
        // getPromptTokens() (no incluidos ahí), con su propio precio: escritura de caché
        // cuesta 1.25x el precio normal de entrada (TTL de 5 minutos, el que se usa aquí),
        // lectura de caché cuesta 0.1x. Si no se suman aquí con su propio factor, el
        // gobernador de presupuesto de arriba subestimaría el gasto real en cuanto el caché
        // empiece a dar hits, permitiendo que el gasto real supere el presupuesto configurado.
        long tokensCacheEscritura = usage.getCacheWriteInputTokens() != null ? usage.getCacheWriteInputTokens() : 0;
        long tokensCacheLectura = usage.getCacheReadInputTokens() != null ? usage.getCacheReadInputTokens() : 0;
        long tokensEntrada = tokensEntradaBase + tokensCacheEscritura + tokensCacheLectura;

        long costoMicroUsd = tokensEntradaBase * costoEntradaMicroUsdPorToken
                + tokensSalida * costoSalidaMicroUsdPorToken
                + (tokensCacheEscritura * costoEntradaMicroUsdPorToken * 5) / 4
                + (tokensCacheLectura * costoEntradaMicroUsdPorToken) / 10;

        // Nivel debug a propósito (no info): es solo para verificar que el prompt caching
        // esté funcionando de verdad en un entorno real (cache_lectura > 0 en conversaciones
        // de varias vueltas), no algo que deba llenar los logs de producción en cada mensaje.
        if (log.isDebugEnabled()) {
            log.debug("Uso Claude — entrada={} cache_escritura={} cache_lectura={} salida={} costo_micro_usd={}",
                    tokensEntradaBase, tokensCacheEscritura, tokensCacheLectura, tokensSalida, costoMicroUsd);
        }
        try {
            conversacionChatbotRepository.registrarUsoChatbot(tokensEntrada, tokensSalida, costoMicroUsd);
        } catch (RuntimeException ex) {
            log.error("No se pudo registrar el uso/costo del chatbot: {}", ex.getMessage(), ex);
        }
    }

    private ConversacionChatbot obtenerOCrearConversacion(Long conversacionId) {
        if (conversacionId != null) {
            return conversacionChatbotRepository.findById(conversacionId)
                    .orElseThrow(() -> new RecursoNoEncontradoException("No existe la conversación con id " + conversacionId));
        }

        // RF-27: solo se valida el tope al iniciar una conversación nueva, no en cada mensaje,
        // ya que cada fila de conversaciones_chatbot representa una conversación completa. Este
        // tope por CANTIDAD es independiente y complementario al gobernador de presupuesto por
        // COSTO de arriba: protege contra volumen alto de conversaciones aunque cada una sea
        // barata, mientras que el presupuesto protege contra conversaciones caras aunque el
        // volumen sea bajo.
        if (conversacionChatbotRepository.contarConversacionesMesActual() >= limiteMensual) {
            throw new LimiteChatbotExcedidoException(
                    "Se alcanzó el límite mensual de conversaciones del chatbot. Por favor contáctenos por WhatsApp.");
        }

        ConversacionChatbot nueva = new ConversacionChatbot();
        nueva.setCantidadMensajes(0);
        return conversacionChatbotRepository.save(nueva);
    }

    private List<Message> construirHistorial(List<TurnoChatDto> historial) {
        List<Message> mensajes = new ArrayList<>();
        if (historial == null) {
            return mensajes;
        }
        for (TurnoChatDto turno : historial) {
            Message mensaje = "ASISTENTE".equals(turno.rol())
                    ? new AssistantMessage(turno.contenido())
                    : new UserMessage(turno.contenido());
            mensajes.add(mensaje);
        }
        return mensajes;
    }
}
