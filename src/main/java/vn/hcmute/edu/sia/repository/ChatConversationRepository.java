package vn.hcmute.edu.sia.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;
import vn.hcmute.edu.sia.entity.ChatConversation;
import vn.hcmute.edu.sia.entity.DirectConversation;

public interface ChatConversationRepository extends MongoRepository<ChatConversation, String> {
    Optional<DirectConversation> findByStudentIdAndAdvisorIdAndConversationType(String studentId, String advisorId, String conversationType);
    List<DirectConversation> findByStudentIdAndConversationType(String studentId, String conversationType);
    List<DirectConversation> findByAdvisorIdAndConversationType(String advisorId, String conversationType);
}
