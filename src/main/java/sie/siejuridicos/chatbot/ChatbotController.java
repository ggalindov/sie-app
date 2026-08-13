package sie.siejuridicos.chatbot;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sie.siejuridicos.chatbot.dto.MensajeChatbotRequest;
import sie.siejuridicos.chatbot.dto.MensajeChatbotResponse;

@RestController
@RequestMapping("/api/chatbot")
public class ChatbotController {

    private final ChatbotService chatbotService;

    public ChatbotController(ChatbotService chatbotService) {
        this.chatbotService = chatbotService;
    }

    @PostMapping("/mensaje")
    public ResponseEntity<MensajeChatbotResponse> mensaje(@Valid @RequestBody MensajeChatbotRequest request) {
        return ResponseEntity.ok(chatbotService.responder(request));
    }
}
