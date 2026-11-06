package vn.hcmute.edu.sia.config;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import vn.hcmute.edu.sia.entity.Account;
import vn.hcmute.edu.sia.entity.AdvisorProfile;
import vn.hcmute.edu.sia.entity.Department;
import vn.hcmute.edu.sia.entity.StudentProfile;
import vn.hcmute.edu.sia.enums.AccountAccessStatus;
import vn.hcmute.edu.sia.enums.AccountRole;
import vn.hcmute.edu.sia.enums.CatalogStatus;
import vn.hcmute.edu.sia.repository.AccountRepository;
import vn.hcmute.edu.sia.repository.AdvisorProfileRepository;
import vn.hcmute.edu.sia.repository.DepartmentRepository;
import vn.hcmute.edu.sia.repository.StudentProfileRepository;

@Configuration
@Profile("dev")
public class DevDataSeeder {

    private static final Logger LOGGER = LoggerFactory.getLogger(DevDataSeeder.class);
    private static final String TEST_STUDENT_EMAIL = "student.test@example.com";
    private static final String TEST_ADVISOR_EMAIL = "advisor.test@example.com";
    private static final String TEST_ADMIN_EMAIL = "admin.test@example.com";
    private static final String TEST_DEPARTMENT_NAME = "Test Department";

    @Bean
    CommandLineRunner seedTestAccounts(
            AccountRepository accountRepository,
            StudentProfileRepository studentProfileRepository,
            AdvisorProfileRepository advisorProfileRepository,
            DepartmentRepository departmentRepository,
            PasswordEncoder passwordEncoder,
            TransactionTemplate transactionTemplate,
            @Value("${SEED_TEST_PASSWORD:${SEED_ADVISOR_PASSWORD:}}") String testPassword
    ) {
        return args -> {
            boolean accountMissing = List.of(
                    TEST_STUDENT_EMAIL,
                    TEST_ADVISOR_EMAIL,
                    TEST_ADMIN_EMAIL
            ).stream().anyMatch(email -> accountRepository.findByEmail(email).isEmpty());

            if (accountMissing && (testPassword == null || testPassword.isBlank())) {
                throw new IllegalStateException(
                        "Set SEED_TEST_PASSWORD before starting with the dev profile"
                );
            }

            transactionTemplate.executeWithoutResult(transactionStatus -> {
                Department department = findOrCreateTestDepartment(departmentRepository);

                Account student = findOrCreateAccount(
                        accountRepository,
                        passwordEncoder,
                        TEST_STUDENT_EMAIL,
                        "Test Student",
                        AccountRole.STUDENT,
                        testPassword
                );
                if (!studentProfileRepository.existsByAccount_Id(student.getId())) {
                    studentProfileRepository.save(new StudentProfile(
                            student,
                            null,
                            null,
                            null,
                            "Local account for student profile testing"
                    ));
                }

                Account advisor = findOrCreateAccount(
                        accountRepository,
                        passwordEncoder,
                        TEST_ADVISOR_EMAIL,
                        "Test Advisor",
                        AccountRole.ADVISOR,
                        testPassword
                );
                if (!advisorProfileRepository.existsByAccount_Id(advisor.getId())) {
                    advisorProfileRepository.save(new AdvisorProfile(
                            advisor,
                            department,
                            "0900000000",
                            "Local account for document upload testing"
                    ));
                }

                findOrCreateAccount(
                        accountRepository,
                        passwordEncoder,
                        TEST_ADMIN_EMAIL,
                        "Test Admin",
                        AccountRole.ADMIN,
                        testPassword
                );
            });

            LOGGER.info(
                    "Ensured dev test accounts for student {}, advisor {}, and admin {}",
                    TEST_STUDENT_EMAIL,
                    TEST_ADVISOR_EMAIL,
                    TEST_ADMIN_EMAIL
            );
        };
    }

    private Account findOrCreateAccount(
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder,
            String email,
            String fullName,
            AccountRole role,
            String testPassword
    ) {
        return accountRepository.findByEmail(email)
                .map(account -> {
                    if (account.getRole() != role) {
                        throw new IllegalStateException(
                                "Seed account has an unexpected role: " + email
                        );
                    }
                    return account;
                })
                .orElseGet(() -> accountRepository.save(new Account(
                        fullName,
                        email,
                        passwordEncoder.encode(testPassword),
                        role,
                        AccountAccessStatus.ACTIVE,
                        false
                )));
    }

    private Department findOrCreateTestDepartment(DepartmentRepository departmentRepository) {
        List<Department> departments = departmentRepository.findAll();
        return departments.stream()
                .filter(item -> item.getStatus() == CatalogStatus.ACTIVE)
                .findFirst()
                .orElseGet(() -> departments.stream()
                        .filter(item -> TEST_DEPARTMENT_NAME.equals(item.getName()))
                        .findFirst()
                        .orElseGet(() -> departmentRepository.save(
                                new Department(TEST_DEPARTMENT_NAME)
                        )));
    }
}
