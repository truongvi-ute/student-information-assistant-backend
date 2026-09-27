package vn.hcmute.edu.sia.dto;

import java.util.UUID;

public record PendingRegistration(
        String fullName,
        String email,
        String passwordHash,
        UUID majorId,
        UUID educationSystemId,
        UUID cohortId,
        String academicContext) {}