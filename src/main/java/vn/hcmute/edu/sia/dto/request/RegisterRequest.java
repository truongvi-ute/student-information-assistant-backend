package vn.hcmute.edu.sia.dto.request;

import java.util.UUID;

public record RegisterRequest(String fullName,
        String email,
        String password,
        UUID majorId,
        UUID educationSystemId,
        UUID cohortId,
        String academicContext) {}
