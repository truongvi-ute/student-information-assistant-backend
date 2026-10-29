package vn.hcmute.edu.sia.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import vn.hcmute.edu.sia.entity.ChatConversation;

public interface ChatConversationRepository extends MongoRepository<ChatConversation, String> {
    Optional<ChatConversation> findByStudentIdAndAdvisorIdAndConversationType(String studentId, String advisorId, String conversationType);
    List<ChatConversation> findByStudentIdAndConversationType(String studentId, String conversationType);
    List<ChatConversation> findByAdvisorIdAndConversationType(String advisorId, String conversationType);
}