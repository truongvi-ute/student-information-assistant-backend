package vn.hcmute.edu.sia.controller;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import vn.hcmute.edu.sia.dto.response.ChatContactResponse;
import vn.hcmute.edu.sia.security.AccountPrincipal;
import vn.hcmute.edu.sia.service.ChatService;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatContactController {
    private final ChatService chatService;

    @GetMapping("/contacts")
    public List<ChatContactResponse> getContacts(
            @AuthenticationPrincipal AccountPrincipal currentUser
    ) {
        return chatService.getContacts(currentUser.getAccountId());
    }
}