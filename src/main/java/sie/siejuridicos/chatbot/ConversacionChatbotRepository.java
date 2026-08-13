package sie.siejuridicos.chatbot;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ConversacionChatbotRepository extends JpaRepository<ConversacionChatbot, Long> {

    // invoca fn_contar_conversaciones_mes_actual (V8): usado para validar el tope de 500/mes antes de llamar a Claude.
    @Query(value = "SELECT fn_contar_conversaciones_mes_actual()", nativeQuery = true)
    int contarConversacionesMesActual();
}
