package com.regulatory.platform.config;

import com.regulatory.platform.entity.Application;
import com.regulatory.platform.entity.StatusHistory;
import com.regulatory.platform.entity.User;
import com.regulatory.platform.enums.ApplicationStatus;
import com.regulatory.platform.enums.LicensingTrack;
import com.regulatory.platform.enums.UserRole;
import com.regulatory.platform.repository.ApiAuditLogRepository;
import com.regulatory.platform.repository.ApplicationRepository;
import com.regulatory.platform.repository.ApplicationRoundSnapshotRepository;
import com.regulatory.platform.repository.ChecklistItemRepository;
import com.regulatory.platform.repository.ClarificationThreadRepository;
import com.regulatory.platform.repository.DocumentRepository;
import com.regulatory.platform.repository.NotificationRepository;
import com.regulatory.platform.repository.OfficerCommentRepository;
import com.regulatory.platform.repository.StatusHistoryRepository;
import com.regulatory.platform.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DataSeeder {
    @Value("${app.seed.enabled:true}")
    private boolean seedEnabled;
    @Value("${app.seed.reset-on-startup:false}")
    private boolean resetOnStartup;

    @Bean
    CommandLineRunner seedData(
            UserRepository userRepository,
            ApiAuditLogRepository apiAuditLogRepository,
            NotificationRepository notificationRepository,
            ApplicationRepository applicationRepository,
            DocumentRepository documentRepository,
            ApplicationRoundSnapshotRepository applicationRoundSnapshotRepository,
            OfficerCommentRepository officerCommentRepository,
            StatusHistoryRepository statusHistoryRepository,
            ChecklistItemRepository checklistItemRepository,
            ClarificationThreadRepository clarificationThreadRepository,
            PasswordEncoder passwordEncoder) {

        return args -> {
            if (!seedEnabled) {
                log.info("Demo seeding disabled via app.seed.enabled=false");
                return;
            }
            if (resetOnStartup) {
                clarificationThreadRepository.deleteAllInBatch();
                checklistItemRepository.deleteAllInBatch();
                officerCommentRepository.deleteAllInBatch();
                documentRepository.deleteAllInBatch();
                applicationRoundSnapshotRepository.deleteAllInBatch();
                statusHistoryRepository.deleteAllInBatch();
                notificationRepository.deleteAllInBatch();
                applicationRepository.deleteAllInBatch();
                apiAuditLogRepository.deleteAllInBatch();
                userRepository.deleteAllInBatch();
            } else if (userRepository.count() > 0) {
                log.info("Existing data found. Skipping demo reset/seed to preserve persisted state.");
                return;
            }

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

            Application app1 = applicationRepository.save(Application.builder()
                    .referenceNumber("LIC-2026-ECDC01")
                    .operator(operator)
                    .assignedOfficer(officer)
                    .status(ApplicationStatus.PENDING_PRE_SITE_RESUBMISSION)
                    .businessName("Little Sprouts Preschool")
                    .licensingTrack(LicensingTrack.ECDC)
                    .businessType("Early Childhood Development Centre")
                    .businessAddress("12 Fernvale Street, Singapore 798123")
                    .contactPhone("+65 9123 4567")
                    .activityDescription("ECDC licence renewal with floor-plan and safety remediation updates")
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
                    .changedBy(officer).notes("Please provide revised floor plan and safety remedial proof").build());

            Application app2 = applicationRepository.save(Application.builder()
                    .referenceNumber("LIC-2026-SCFA01")
                    .operator(operator2)
                    .assignedOfficer(officer)
                    .status(ApplicationStatus.SITE_VISIT_SCHEDULED)
                    .businessName("Bright Minds Student Care")
                    .licensingTrack(LicensingTrack.SCFA)
                    .businessType("Student Care Centre")
                    .businessAddress("89 Jurong East Ave 1, Singapore 609777")
                    .contactPhone("+65 8765 4321")
                    .activityDescription("SCFA compliance audit for subsidy and attendance controls")
                    .submissionRound(2)
                    .build());
            statusHistoryRepository.save(StatusHistory.builder()
                    .application(app2).fromStatus(null)
                    .toStatus(ApplicationStatus.APPLICATION_RECEIVED)
                    .changedBy(operator2).notes("Initial submission").build());
            statusHistoryRepository.save(StatusHistory.builder()
                    .application(app2)
                    .fromStatus(ApplicationStatus.APPLICATION_RECEIVED)
                    .toStatus(ApplicationStatus.UNDER_REVIEW)
                    .changedBy(officer).notes("Reviewed and queued for site visit audit").build());
            statusHistoryRepository.save(StatusHistory.builder()
                    .application(app2)
                    .fromStatus(ApplicationStatus.UNDER_REVIEW)
                    .toStatus(ApplicationStatus.SITE_VISIT_SCHEDULED)
                    .changedBy(officer).notes("Site visit scheduled for subsidy verification").build());

            Application app3 = applicationRepository.save(Application.builder()
                    .referenceNumber("LIC-2026-HFAA01")
                    .operator(operator)
                    .assignedOfficer(officer)
                    .status(ApplicationStatus.UNDER_REVIEW)
                    .businessName("Silver Years Sheltered Home")
                    .licensingTrack(LicensingTrack.HFAA)
                    .businessType("Home for the Aged")
                    .businessAddress("55 Lorong Chencharu, Singapore 769123")
                    .contactPhone("+65 9123 4567")
                    .activityDescription("HFAA inspection readiness with staffing and sanitation controls")
                    .submissionRound(1)
                    .build());
            statusHistoryRepository.save(StatusHistory.builder()
                    .application(app3).fromStatus(null)
                    .toStatus(ApplicationStatus.APPLICATION_RECEIVED)
                    .changedBy(operator).notes("Initial submission").build());
            statusHistoryRepository.save(StatusHistory.builder()
                    .application(app3)
                    .fromStatus(ApplicationStatus.APPLICATION_RECEIVED)
                    .toStatus(ApplicationStatus.UNDER_REVIEW)
                    .changedBy(officer).notes("Pending unannounced monitoring visit").build());

            Application app4 = applicationRepository.save(Application.builder()
                    .referenceNumber("LIC-2026-CHM01")
                    .operator(operator2)
                    .assignedOfficer(officer)
                    .status(ApplicationStatus.APPLICATION_RECEIVED)
                    .businessName("NurtureNest Childminding Home")
                    .licensingTrack(LicensingTrack.CHILDMINDING)
                    .businessType("Childminding Pilot")
                    .businessAddress("302 Yishun Ring Road, Singapore 760302")
                    .contactPhone("+65 8765 4321")
                    .activityDescription("Home assessment for infant-care regulatory sandbox")
                    .submissionRound(1)
                    .build());
            statusHistoryRepository.save(StatusHistory.builder()
                    .application(app4).fromStatus(null)
                    .toStatus(ApplicationStatus.APPLICATION_RECEIVED)
                    .changedBy(operator2).notes("Initial submission").build());

            log.info("═══════════════════════════════════════════════════════════");
            log.info("  Demo data reset and seeded for tracks: ECDC, SCFA, HFAA, CHILDMINDING");
            log.info("  Officer : officer@gov.sg  / password");
            log.info("  Operator: operator@acme.sg / password");
            log.info("  Operator: operator2@beta.sg / password");
            log.info("  H2 Console: http://localhost:8080/h2-console");
            log.info("═══════════════════════════════════════════════════════════");
        };
    }
}
