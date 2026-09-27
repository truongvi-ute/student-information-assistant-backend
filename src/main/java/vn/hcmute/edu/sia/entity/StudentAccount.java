package vn.hcmute.edu.sia.entity;
import vn.hcmute.edu.sia.enums.*;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;

@Entity
@Table(name = "student_profiles")
@PrimaryKeyJoinColumn(name = "student_id")
public class StudentAccount extends Account{
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "major_id")
    private Major major;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "education_system_id")
    private EducationSystem educationSystem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cohort_id")
    private Cohort cohort;

    @Column(name = "academic_context", length = 1000)
    private String academicContext;

    protected StudentAccount() {
        super();
        // Constructor required by JPA.
    }

    public StudentAccount(
            String fullName,
            String email,
            String passwordHash,
            Major major,
            EducationSystem educationSystem,
            Cohort cohort,
            String academicContext
    ) {
        super(
                fullName,
                email,
                passwordHash,
                AccountRole.STUDENT,
                AccountAccessStatus.ACTIVE,
                false
        );

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
