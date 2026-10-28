package vn.hcmute.edu.sia.repository;

import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.mongodb.repository.MongoRepository;
import vn.hcmute.edu.sia.entity.ChatMessage;

public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {
    Slice<ChatMessage> findByConversationIdOrderByCreatedAtDescIdDesc(String conversationId, Pageable pageable);
    Optional<ChatMessage> findFirstByConversationIdOrderByCreatedAtDesc(String conversationId);
}