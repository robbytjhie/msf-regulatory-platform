package com.regulatory.platform.config;

import com.regulatory.platform.entity.*;
import com.regulatory.platform.enums.ApplicationStatus;
import com.regulatory.platform.enums.ChecklistItemStatus;
import com.regulatory.platform.enums.UserRole;
import com.regulatory.platform.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DataSeeder {

    @Bean
    CommandLineRunner seedData(
            UserRepository userRepository,
            ApplicationRepository applicationRepository,
            StatusHistoryRepository statusHistoryRepository,
            ChecklistItemRepository checklistItemRepository,
            PasswordEncoder passwordEncoder) {

        return args -> {
            if (userRepository.count() > 0) return; // idempotent

            // ── Users ─────────────────────────────────────────────
            User officer = userRepository.save(User.builder()
                    .email("officer@gov.sg")
                    .password(passwordEncoder.encode("password"))
                    .fullName("James Tan")
                    .role(UserRole.OFFICER)
                    .build());

            User operator = userRepository.save(User.builder()
                    .email("operator@acme.sg")
                    .password(passwordEncoder.encode("password"))
                    .fullName("Sarah Lim")
                    .role(UserRole.OPERATOR)
                    .organisationName("ACME Pte Ltd")
                    .build());

            User operator2 = userRepository.save(User.builder()
                    .email("operator2@beta.sg")
                    .password(passwordEncoder.encode("password"))
                    .fullName("David Ng")
                    .role(UserRole.OPERATOR)
                    .organisationName("Beta Enterprises")
                    .build());

            // ── Application 1: Awaiting resubmission (UC1 demo) ───
            Application app1 = applicationRepository.save(Application.builder()
                    .referenceNumber("LIC-2025-DEMO01")
                    .operator(operator)
                    .assignedOfficer(officer)
                    .status(ApplicationStatus.PENDING_PRE_SITE_RESUBMISSION)
                    .businessName("ACME Food Catering")
                    .businessType("Food & Beverage")
                    .businessAddress("123 Orchard Road, #04-01, Singapore 238858")
                    .contactPhone("+65 9123 4567")
                    .activityDescription("Large scale catering operations for corporate events")
                    .submissionRound(1)
                    .build());

            statusHistoryRepository.save(StatusHistory.builder()
                    .application(app1).fromStatus(null)
                    .toStatus(ApplicationStatus.APPLICATION_RECEIVED)
                    .changedBy(operator).notes("Initial submission").build());

            statusHistoryRepository.save(StatusHistory.builder()
                    .application(app1)
                    .fromStatus(ApplicationStatus.APPLICATION_RECEIVED)
                    .toStatus(ApplicationStatus.UNDER_REVIEW)
                    .changedBy(officer).notes("Picked up for review").build());

            statusHistoryRepository.save(StatusHistory.builder()
                    .application(app1)
                    .fromStatus(ApplicationStatus.UNDER_REVIEW)
                    .toStatus(ApplicationStatus.PENDING_PRE_SITE_RESUBMISSION)
                    .changedBy(officer).notes("Additional documents required").build());

            // ── Application 2: Site visit scheduled (UC3 demo) ────
            Application app2 = applicationRepository.save(Application.builder()
                    .referenceNumber("LIC-2025-DEMO02")
                    .operator(operator2)
                    .assignedOfficer(officer)
                    .status(ApplicationStatus.SITE_VISIT_SCHEDULED)
                    .businessName("Beta Manufacturing")
                    .businessType("Manufacturing")
                    .businessAddress("456 Tuas Avenue, Singapore 638365")
                    .contactPhone("+65 8765 4321")
                    .activityDescription("Chemical manufacturing and storage facility")
                    .submissionRound(2)
                    .build());

            // Seed checklist items for app2
            List<ChecklistItem> checklistItems = List.of(
                    ChecklistItem.builder().application(app2).itemCode("FIRE_01")
                            .itemTitle("Fire Suppression System").sortOrder(1)
                            .itemDescription("Verify automatic fire suppression system is installed and certified")
                            .status(ChecklistItemStatus.PENDING).build(),
                    ChecklistItem.builder().application(app2).itemCode("FIRE_02")
                            .itemTitle("Emergency Exit Signage").sortOrder(2)
                            .itemDescription("All emergency exits clearly marked with illuminated signage")
                            .status(ChecklistItemStatus.PENDING).build(),
                    ChecklistItem.builder().application(app2).itemCode("CHEM_01")
                            .itemTitle("Chemical Storage Segregation").sortOrder(3)
                            .itemDescription("Incompatible chemicals stored separately per safety regulations")
                            .status(ChecklistItemStatus.PENDING).build(),
                    ChecklistItem.builder().application(app2).itemCode("CHEM_02")
                            .itemTitle("Spill Containment").sortOrder(4)
                            .itemDescription("Adequate spill containment measures in all storage areas")
                            .status(ChecklistItemStatus.PENDING).build(),
                    ChecklistItem.builder().application(app2).itemCode("ELEC_01")
                            .itemTitle("Electrical Safety Certificate").sortOrder(5)
                            .itemDescription("Valid electrical safety certificate from licensed contractor")
                            .status(ChecklistItemStatus.PENDING).build()
            );
            checklistItemRepository.saveAll(checklistItems);

            // ── Application 3: Approved (terminal state) ──────────
            Application app3 = applicationRepository.save(Application.builder()
                    .referenceNumber("LIC-2025-DEMO03")
                    .operator(operator)
                    .assignedOfficer(officer)
                    .status(ApplicationStatus.APPROVED)
                    .businessName("ACME Retail Shop")
                    .businessType("Retail")
                    .businessAddress("789 Bugis Street, Singapore 188867")
                    .contactPhone("+65 9123 4567")
                    .activityDescription("General retail merchandise")
                    .submissionRound(1)
                    .build());

            log.info("═══════════════════════════════════════════════════════════");
            log.info("  Demo data seeded. Login credentials:");
            log.info("  Officer : officer@gov.sg  / password");
            log.info("  Operator: operator@acme.sg / password");
            log.info("  Operator: operator2@beta.sg / password");
            log.info("  H2 Console: http://localhost:8080/h2-console");
            log.info("═══════════════════════════════════════════════════════════");
        };
    }
}
