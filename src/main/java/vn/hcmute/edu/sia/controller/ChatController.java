package vn.hcmute.edu.sia.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import vn.hcmute.edu.sia.dto.request.SendChatMessageRequest;
import vn.hcmute.edu.sia.entity.Account;
import vn.hcmute.edu.sia.entity.ChatMessage;
import vn.hcmute.edu.sia.security.AccountPrincipal;
import vn.hcmute.edu.sia.service.ChatService;

import java.security.Principal;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @MessageMapping("/sendMessage")
    public void sendMessage(SendChatMessageRequest request, Principal principal) {
        Authentication authentication = (Authentication) principal;
        AccountPrincipal sender =
                (AccountPrincipal) authentication.getPrincipal();

        chatService.sendPrivateMessage(
                sender.getAccountId(),
                request
        );
    }
}
