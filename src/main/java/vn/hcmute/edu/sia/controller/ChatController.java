package vn.hcmute.edu.sia.controller;

import java.security.Principal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.server.ResponseStatusException;
import vn.hcmute.edu.sia.dto.request.SendChatMessageRequest;
import vn.hcmute.edu.sia.dto.response.ChatErrorEvent;
import vn.hcmute.edu.sia.entity.UserMessage;
import vn.hcmute.edu.sia.security.AccountPrincipal;
import vn.hcmute.edu.sia.service.ChatService;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatController {

    private static final String USER_QUEUE_ERRORS = "/queue/errors";

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/sendMessage")
    public void sendMessage(SendChatMessageRequest request, Principal principal) {
        if (!(principal instanceof Authentication authentication)
                || !(authentication.getPrincipal() instanceof AccountPrincipal sender)) {
            return;
        }

        String clientMessageId = request != null
                ? sanitizeClientMessageIdForError(request.clientMessageId())
                : null;
        String recipientId = request != null && request.recipientId() != null
                ? request.recipientId().toString()
                : null;

        if (request == null || request.recipientId() == null) {
            messagingTemplate.convertAndSendToUser(
                    sender.getUsername(),
                    USER_QUEUE_ERRORS,
                    ChatErrorEvent.of(clientMessageId, recipientId, "Recipient ID must not be null")
            );
            return;
        }

        try {
            chatService.sendPrivateMessage(
                    sender.getAccountId(),
                    request
            );
        } catch (IllegalArgumentException | ResponseStatusException exception) {
            String message = exception instanceof ResponseStatusException rse && rse.getReason() != null
                    ? rse.getReason()
                    : exception.getMessage();
            messagingTemplate.convertAndSendToUser(
                    sender.getUsername(),
                    USER_QUEUE_ERRORS,
                    ChatErrorEvent.of(clientMessageId, recipientId, message)
            );
        } catch (Exception exception) {
            log.error(
                    "Unexpected error while sending private chat message from {} to {}",
                    sender.getAccountId(),
                    recipientId,
                    exception
            );
            messagingTemplate.convertAndSendToUser(
                    sender.getUsername(),
                    USER_QUEUE_ERRORS,
                    ChatErrorEvent.of(
                            clientMessageId,
                            recipientId,
                            "Không thể gửi tin nhắn lúc này, vui lòng thử lại."
                    )
            );
        }
    }

    private static String sanitizeClientMessageIdForError(String rawClientMessageId) {
        if (rawClientMessageId == null) {
            return null;
        }
        String stripped = rawClientMessageId.strip();
        if (stripped.isEmpty()) {
            return null;
        }
        return stripped.length() > UserMessage.MAX_CLIENT_MESSAGE_ID_LENGTH
                ? stripped.substring(0, UserMessage.MAX_CLIENT_MESSAGE_ID_LENGTH)
                : stripped;
    }
}
