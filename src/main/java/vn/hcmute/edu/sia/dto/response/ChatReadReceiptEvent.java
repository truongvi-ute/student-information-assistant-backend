package vn.hcmute.edu.sia.dto.response;

import java.time.Instant;

public record ChatReadReceiptEvent(
        String conversationId,
        String readerId,
        String partnerId,
        Instant lastReadAt,
        String type
) {
    public static ChatReadReceiptEvent of(
            String conversationId,
            String readerId,
            String partnerId,
            Instant lastReadAt
    ) {
        return new ChatReadReceiptEvent(
                conversationId,
                readerId,
                partnerId,
                lastReadAt,
                "READ_RECEIPT"
        );
    }
}
