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

import vn.hcmute.edu.sia.entity.AdvisorAccount;
import vn.hcmute.edu.sia.entity.Department;
import vn.hcmute.edu.sia.enums.CatalogStatus;
import vn.hcmute.edu.sia.repository.AccountRepository;
import vn.hcmute.edu.sia.repository.DepartmentRepository;

@Configuration
@Profile("dev")
public class DevDataSeeder {

    private static final Logger LOGGER = LoggerFactory.getLogger(DevDataSeeder.class);
    private static final String TEST_ADVISOR_EMAIL = "advisor.test@example.com";
    private static final String TEST_DEPARTMENT_NAME = "Test Department";

    @Bean
    CommandLineRunner seedTestAdvisor(
            AccountRepository accountRepository,
            DepartmentRepository departmentRepository,
            PasswordEncoder passwordEncoder,
            @Value("${SEED_ADVISOR_PASSWORD}") String advisorPassword
    ) {
        return args -> {
            if (accountRepository.existsByEmail(TEST_ADVISOR_EMAIL)) {
                LOGGER.info("Test advisor already exists: {}", TEST_ADVISOR_EMAIL);
                return;
            }
            if (advisorPassword == null || advisorPassword.isBlank()) {
                throw new IllegalStateException(
                        "Set SEED_ADVISOR_PASSWORD before starting with the dev profile"
                );
            }

            List<Department> departments = departmentRepository.findAll();
            Department department = departments.stream()
                    .filter(item -> item.getStatus() == CatalogStatus.ACTIVE)
                    .findFirst()
                    .orElseGet(() -> departments.stream()
                            .filter(item -> TEST_DEPARTMENT_NAME.equals(item.getName()))
                            .findFirst()
                            .orElseGet(() -> departmentRepository.save(
                                    new Department(TEST_DEPARTMENT_NAME)
                            )));

            AdvisorAccount advisor = new AdvisorAccount(
                    "Test Advisor",
                    TEST_ADVISOR_EMAIL,
                    passwordEncoder.encode(advisorPassword),
                    department,
                    "0900000000",
                    "Local account for document upload testing"
            );

            accountRepository.save(advisor);
            LOGGER.info(
                    "Created test advisor {} for department {}",
                    TEST_ADVISOR_EMAIL,
                    department.getName()
            );
        };
    }
}
