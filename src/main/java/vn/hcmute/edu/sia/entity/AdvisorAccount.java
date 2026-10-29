package vn.hcmute.edu.sia.entity;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.hcmute.edu.sia.enums.AccountAccessStatus;
import vn.hcmute.edu.sia.enums.AccountRole;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "advisor_profiles")
@PrimaryKeyJoinColumn(name = "advisor_id")
public class AdvisorAccount extends Account {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    public AdvisorAccount(
            String fullName,
            String email,
            String passwordHash,
            Department department,
            String phoneNumber,
            String note
    ) {
        super(
                fullName,
                email,
                passwordHash,
                AccountRole.ADVISOR,
                AccountAccessStatus.ACTIVE,
                false
        );
        this.department = Objects.requireNonNull(
                department,
                "Department must not be null"
        );
        this.phoneNumber = normalizePhoneNumber(phoneNumber);
        this.note = normalizeOptionalText(note);
    }

    public void changeDepartment(Department department) {
        this.department = Objects.requireNonNull(
                department,
                "Department must not be null"
        );
    }

    public void updateContactInfo(String phoneNumber, String note) {
        this.phoneNumber = normalizePhoneNumber(phoneNumber);
        this.note = normalizeOptionalText(note);
    }

    private String normalizePhoneNumber(String phoneNumber) {
        String normalized = normalizeOptionalText(phoneNumber);
        if (normalized != null && normalized.length() > 20) {
            throw new IllegalArgumentException(
                    "Phone number must not exceed 20 characters"
            );
        }
        return normalized;
    }

    private String normalizeOptionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
