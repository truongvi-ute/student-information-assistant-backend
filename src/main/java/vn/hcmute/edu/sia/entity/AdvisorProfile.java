package vn.hcmute.edu.sia.entity;

import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import vn.hcmute.edu.sia.enums.AccountRole;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "advisor_profiles")
public class AdvisorProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "advisor_profile_id", nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            referencedColumnName = "user_id",
            nullable = false,
            unique = true
    )
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(name = "phone_number", length = 20)
    private String phoneNumber;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    public AdvisorProfile(
            Account account,
            Department department,
            String phoneNumber,
            String note
    ) {
        this.account = Objects.requireNonNull(account, "Account must not be null");
        if (account.getRole() != AccountRole.ADVISOR) {
            throw new IllegalArgumentException("Advisor profile requires an advisor account");
        }

        this.department = Objects.requireNonNull(department, "Department must not be null");
        this.phoneNumber = normalizePhoneNumber(phoneNumber);
        this.note = normalizeOptionalText(note);
    }

    public void changeDepartment(Department department) {
        this.department = Objects.requireNonNull(department, "Department must not be null");
    }

    public void updateContactInfo(String phoneNumber, String note) {
        this.phoneNumber = normalizePhoneNumber(phoneNumber);
        this.note = normalizeOptionalText(note);
    }

    private String normalizePhoneNumber(String phoneNumber) {
        String normalized = normalizeOptionalText(phoneNumber);
        if (normalized != null && !normalized.matches("[0-9]{10}")) {
            throw new IllegalArgumentException("Phone number must contain exactly 10 digits");
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
