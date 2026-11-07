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
@Table(name = "student_profiles")
public class StudentProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "student_profile_id", nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            referencedColumnName = "user_id",
            nullable = false,
            unique = true
    )
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "major_id")
    private Major major;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "education_system_id")
    private EducationSystem educationSystem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cohort_id")
    private Cohort cohort;

    @Column(name = "academic_context", columnDefinition = "TEXT")
    private String academicContext;

    public StudentProfile(
            Account account,
            Major major,
            EducationSystem educationSystem,
            Cohort cohort,
            String academicContext
    ) {
        this.account = Objects.requireNonNull(account, "Account must not be null");
        if (account.getRole() != AccountRole.STUDENT) {
            throw new IllegalArgumentException("Student profile requires a student account");
        }

        this.major = major;
        this.educationSystem = educationSystem;
        this.cohort = cohort;
        this.academicContext = normalizeAcademicContext(academicContext);
    }

    private String normalizeAcademicContext(String academicContext) {
        if (academicContext == null || academicContext.isBlank()) {
            return null;
        }
        return academicContext.trim();
    }
}
