package vn.hcmute.edu.sia.dto.response;

import vn.hcmute.edu.sia.enums.AccountRole;
import java.util.UUID;

public record ChatContactResponse(
        UUID accountId,
        String fullName,
        AccountRole role,
        UUID departmentId,
        String departmentName
) {
    public ChatContactResponse(UUID accountId, String fullName, AccountRole role) {
        this(accountId, fullName, role, null, null);
    }
}
