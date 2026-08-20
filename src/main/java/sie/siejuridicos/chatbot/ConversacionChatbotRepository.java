package sie.siejuridicos.chatbot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversacionChatbotRepository extends JpaRepository<ConversacionChatbot, Long> {

    // invoca fn_contar_conversaciones_mes_actual (V8): usado para validar el tope de 500/mes antes de llamar a Claude.
    @Query(value = "SELECT fn_contar_conversaciones_mes_actual()", nativeQuery = true)
    int contarConversacionesMesActual();

    // invoca fn_registrar_uso_chatbot / fn_costo_mensual_chatbot (V14): gobernador de
    // presupuesto real del chatbot, ver ChatbotService. No están en un repositorio propio
    // porque Spring Data JPA no puede crear un repositorio "sin entidad" (Repository<Object,
    // Void> falla al arrancar con "Not a managed type: class java.lang.Object"); como estas
    // dos funciones no gestionan ninguna tabla con entidad JPA propia, viven aquí, en el
    // repositorio del módulo con el que más relación tienen.
    @Query(value = "SELECT fn_registrar_uso_chatbot(:tokensEntrada, :tokensSalida, :costoMicroUsd)", nativeQuery = true)
    long registrarUsoChatbot(
            @Param("tokensEntrada") long tokensEntrada,
            @Param("tokensSalida") long tokensSalida,
            @Param("costoMicroUsd") long costoMicroUsd);

    @Query(value = "SELECT fn_costo_mensual_chatbot()", nativeQuery = true)
    long obtenerCostoMensualChatbotMicroUsd();
}
