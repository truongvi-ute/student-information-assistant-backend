package vn.hcmute.edu.sia.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import vn.hcmute.edu.sia.dto.request.MarkChatReadRequest;
import vn.hcmute.edu.sia.dto.response.ChatConversationResponse;
import vn.hcmute.edu.sia.dto.response.DepartmentResponse;
import vn.hcmute.edu.sia.dto.response.MarkChatReadResponse;
import vn.hcmute.edu.sia.security.AccountPrincipal;
import vn.hcmute.edu.sia.service.ChatService;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatConversationController {
    private final ChatService chatService;

    @GetMapping("/conversations")
    public List<ChatConversationResponse> getConversations(
            @AuthenticationPrincipal AccountPrincipal currentUser
    ) {
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }

        return chatService.getConversations(currentUser.getAccountId());
    }

    @PostMapping("/conversations/read")
    public MarkChatReadResponse markConversationAsRead(
            @AuthenticationPrincipal AccountPrincipal currentUser,
            @RequestBody(required = false) MarkChatReadRequest request
    ) {
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }

        return chatService.markConversationAsRead(currentUser.getAccountId(), request);
    }

    @GetMapping("/departments")
    public List<DepartmentResponse> getDepartments() {
        return chatService.getDepartments();
    }
}
