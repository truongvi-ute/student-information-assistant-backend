package vn.hcmute.edu.sia.service.implement;

import java.io.IOException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.hcmute.edu.sia.service.DocumentStorageService;

@Component
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class DraftStorageCleanup {

    private final JdbcTemplate jdbcTemplate;
    private final DocumentStorageService storageService;

    @Scheduled(fixedDelay = 15000, initialDelay = 15000)
    public void removeDeletedDraftFiles() {
        var paths = jdbcTemplate.queryForList("""
                SELECT storage_path FROM document_storage_cleanup
                WHERE retry_at <= CURRENT_TIMESTAMP ORDER BY retry_at LIMIT 20
                """, String.class);
        for (String path : paths) {
            try {
                storageService.delete(path);
                jdbcTemplate.update("DELETE FROM document_storage_cleanup WHERE storage_path = ?", path);
            } catch (IOException | RuntimeException exception) {
                jdbcTemplate.update("""
                        UPDATE document_storage_cleanup SET retry_at = CURRENT_TIMESTAMP + INTERVAL '1 minute'
                        WHERE storage_path = ?
                        """, path);
                log.warn("Could not remove a deleted draft from storage", exception);
            }
        }
    }
}
