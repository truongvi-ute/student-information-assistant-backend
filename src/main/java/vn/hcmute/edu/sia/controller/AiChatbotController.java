package vn.hcmute.edu.sia.controller;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import vn.hcmute.edu.sia.dto.request.CreateAiConversationRequest;
import vn.hcmute.edu.sia.dto.request.SendAiMessageRequest;
import vn.hcmute.edu.sia.dto.response.AiChatbotHistoryResponse;
import vn.hcmute.edu.sia.dto.response.AiChatbotTurnResponse;
import vn.hcmute.edu.sia.dto.response.AiConversationResponse;
import vn.hcmute.edu.sia.security.AccountPrincipal;
import vn.hcmute.edu.sia.service.AiChatbotService;

@RestController
@RequestMapping("/api/chat/ai")
@RequiredArgsConstructor
public class AiChatbotController {

    private final AiChatbotService aiChatbotService;

    @PostMapping("/conversations")
    @ResponseStatus(HttpStatus.CREATED)
    public AiConversationResponse createConversation(
            @AuthenticationPrincipal AccountPrincipal currentUser,
            @Valid @RequestBody(required = false) CreateAiConversationRequest request
    ) {
        UUID studentId = requireAccountId(currentUser);
        return aiChatbotService.createConversation(studentId, request);
    }

    @GetMapping("/conversations")
    public List<AiConversationResponse> getConversations(
            @AuthenticationPrincipal AccountPrincipal currentUser
    ) {
        UUID studentId = requireAccountId(currentUser);
        return aiChatbotService.getConversations(studentId);
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public AiChatbotHistoryResponse getMessages(
            @AuthenticationPrincipal AccountPrincipal currentUser,
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size
    ) {
        UUID studentId = requireAccountId(currentUser);
        return aiChatbotService.getMessages(studentId, conversationId, page, size);
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public AiChatbotTurnResponse askQuestion(
            @AuthenticationPrincipal AccountPrincipal currentUser,
            @PathVariable String conversationId,
            @Valid @RequestBody SendAiMessageRequest request
    ) {
        UUID studentId = requireAccountId(currentUser);
        return aiChatbotService.askQuestion(studentId, conversationId, request);
    }

    @DeleteMapping("/conversations/{conversationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteConversation(
            @AuthenticationPrincipal AccountPrincipal currentUser,
            @PathVariable String conversationId
    ) {
        UUID studentId = requireAccountId(currentUser);
        aiChatbotService.deleteConversation(studentId, conversationId);
    }

    private UUID requireAccountId(AccountPrincipal currentUser) {
        if (currentUser == null || currentUser.getAccountId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }
        return currentUser.getAccountId();
    }
}
