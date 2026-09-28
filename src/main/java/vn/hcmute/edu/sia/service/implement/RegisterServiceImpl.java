package vn.hcmute.edu.sia.service.implement;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import org.springframework.stereotype.Service;
import vn.hcmute.edu.sia.dto.PendingRegistration;
import vn.hcmute.edu.sia.dto.request.RegisterRequest;
import vn.hcmute.edu.sia.dto.response.OtpResendCooldownResponse;
import vn.hcmute.edu.sia.enums.OtpPurpose;
import vn.hcmute.edu.sia.repository.AccountRepository;
import vn.hcmute.edu.sia.repository.PendingRegistrationRepository;
import vn.hcmute.edu.sia.repository.MajorRepository;
import vn.hcmute.edu.sia.repository.CohortRepository;
import vn.hcmute.edu.sia.repository.EducationSystemRepository;
import vn.hcmute.edu.sia.service.EmailService;
import vn.hcmute.edu.sia.service.OtpService;
import vn.hcmute.edu.sia.service.RegisterService;
import vn.hcmute.edu.sia.entity.EducationSystem;
import vn.hcmute.edu.sia.entity.Major;
import vn.hcmute.edu.sia.entity.Cohort;
import vn.hcmute.edu.sia.entity.StudentAccount;
@Service
public class RegisterServiceImpl implements RegisterService {

    private static final Duration PENDING_REGISTRATION_TTL =
            Duration.ofMinutes(10);

    private final AccountRepository accountRepository;
    private final PendingRegistrationRepository pendingRegistrationRepository;
    private final MajorRepository majorRepository;
    private final EducationSystemRepository educationSystemRepository;
    private final CohortRepository cohortRepository;
    private final OtpService otpService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    public RegisterServiceImpl(
        AccountRepository accountRepository,
        PendingRegistrationRepository pendingRegistrationRepository,
        MajorRepository majorRepository,
        EducationSystemRepository educationSystemRepository,
        CohortRepository cohortRepository,
        OtpService otpService,
        EmailService emailService,
        PasswordEncoder passwordEncoder) {
            this.accountRepository = accountRepository;
            this.pendingRegistrationRepository = pendingRegistrationRepository;
            this.majorRepository = majorRepository;
            this.educationSystemRepository = educationSystemRepository;
            this.cohortRepository = cohortRepository;
            this.otpService = otpService;
            this.emailService = emailService;
            this.passwordEncoder = passwordEncoder;
    }

    @Override
    public OtpResendCooldownResponse startRegistration(RegisterRequest request) {
        String email = normalizeEmail(request.email());

        if (accountRepository.existsByEmail(email)) {
            throw new IllegalStateException("Email already exists.");
        }
        String passwordHash = passwordEncoder.encode(request.password());
        PendingRegistration pendingRegistration = new PendingRegistration(
                                                                        request.fullName(),
                                                                        email,
                                                                        passwordHash,
                                                                        request.majorId(),
                                                                        request.educationSystemId(),
                                                                        request.cohortId(),
                                                                        request.academicContext()
                                                                );
        String otp = otpService.issueOtp(email, OtpPurpose.REGISTER);
        pendingRegistrationRepository.save(
                email,
                pendingRegistration,
                PENDING_REGISTRATION_TTL
        );
        emailService.sendOtp(email, otp);
        Duration resendCooldown = otpService.getResendCooldownRemaining(email, OtpPurpose.REGISTER);

        return new OtpResendCooldownResponse("OTP has been sent.",resendCooldown.toSeconds());
    }

    @Override
    public void verifyRegistration(String email, String otp) {
        String normalizedEmail = normalizeEmail(email);

        PendingRegistration pendingRegistration =
                pendingRegistrationRepository
                        .findByEmail(normalizedEmail)
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "Pending registration not found or expired."
                                )
                        );
        boolean otpValid = otpService.verifyOtp(
                normalizedEmail,
                OtpPurpose.REGISTER,
                otp
        );

        if (!otpValid) {
            throw new IllegalArgumentException(
                    "OTP is invalid or expired."
            );
        }

        if (accountRepository.existsByEmail(normalizedEmail)) {
            pendingRegistrationRepository.deleteByEmail(normalizedEmail);

            throw new IllegalStateException(
                    "Email already exists."
            );
        }

        Major major = pendingRegistration.majorId() == null
                ? null
                : majorRepository
                        .findById(pendingRegistration.majorId())
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Major not found."
                                )
                        );

        EducationSystem educationSystem =
                pendingRegistration.educationSystemId() == null
                        ? null
                        : educationSystemRepository
                                .findById(
                                        pendingRegistration.educationSystemId()
                                )
                                .orElseThrow(() ->
                                        new IllegalArgumentException(
                                                "Education system not found."
                                        )
                                );

        Cohort cohort = pendingRegistration.cohortId() == null
                ? null
                : cohortRepository
                        .findById(pendingRegistration.cohortId())
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Cohort not found."
                                )
                        );

        StudentAccount studentAccount =
                new StudentAccount(
                        pendingRegistration.fullName(),
                        pendingRegistration.email(),
                        pendingRegistration.passwordHash(),
                        major,
                        educationSystem,
                        cohort,
                        pendingRegistration.academicContext()
                );

        accountRepository.save(studentAccount);

        pendingRegistrationRepository.deleteByEmail(normalizedEmail);
    }
    //helper
    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException(
                    "Email must not be blank"
            );
        }
        return email.trim().toLowerCase();
    }   
}
