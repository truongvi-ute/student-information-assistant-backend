package vn.hcmute.edu.sia.dto.response;

import java.util.UUID;
import vn.hcmute.edu.sia.enums.AccountRole;

public record AccountProfileResponse(
        UUID accountId,
        String fullName,
        String email,
        AccountRole role
) {}