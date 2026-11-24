package vn.hcmute.edu.sia.dto.response;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public record ChatErrorEvent(
        String clientMessageId,
        String recipientId,
        String message,
        String type,
        Instant timestamp
) {
    public static ChatErrorEvent of(
            String clientMessageId,
            String recipientId,
            String message
    ) {
        return new ChatErrorEvent(
                clientMessageId,
                recipientId,
                message,
                "ERROR",
                Instant.now().truncatedTo(ChronoUnit.MILLIS)
        );
    }
}
