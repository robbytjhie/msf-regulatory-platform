package com.regulatory.platform.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulatory.platform.entity.Application;
import com.regulatory.platform.entity.ChecklistItem;
import com.regulatory.platform.entity.User;
import com.regulatory.platform.enums.ApplicationStatus;
import com.regulatory.platform.enums.ChecklistItemStatus;
import com.regulatory.platform.enums.LicensingTrack;
import com.regulatory.platform.enums.UserRole;
import com.regulatory.platform.repository.*;
import com.regulatory.platform.security.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

/**
 * Base class for all integration tests.
 *
 * NOTE: No @Transactional here — MockMvc runs in a separate thread from the test,
 * so a test-level transaction would not be visible to the service layer.
 * Instead we use @DirtiesContext to reset H2 between test classes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(TestDataSeederOverride.class)
public abstract class IntegrationTestBase {

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected UserRepository userRepository;
    @Autowired protected ApplicationRepository applicationRepository;
    @Autowired protected ChecklistItemRepository checklistItemRepository;
    @Autowired protected StatusHistoryRepository statusHistoryRepository;
    @Autowired protected OfficerCommentRepository officerCommentRepository;
    @Autowired protected JwtService jwtService;
    @Autowired protected PasswordEncoder passwordEncoder;

    protected User officer;
    protected User operator;
    protected User operator2;

    protected void seedUsers() {
        // Delete in case a prior test in this class left data
        officerCommentRepository.deleteAll();
        statusHistoryRepository.deleteAll();
        checklistItemRepository.deleteAll();
        applicationRepository.deleteAll();
        userRepository.deleteAll();

        officer = userRepository.save(User.builder()
                .email("officer@test.gov.sg")
                .password(passwordEncoder.encode("password"))
                .fullName("Test Officer")
                .role(UserRole.OFFICER)
                .build());

        operator = userRepository.save(User.builder()
                .email("operator@test.com")
                .password(passwordEncoder.encode("password"))
                .fullName("Test Operator")
                .role(UserRole.OPERATOR)
                .organisationName("Test Corp")
                .build());

        operator2 = userRepository.save(User.builder()
                .email("operator2@test.com")
                .password(passwordEncoder.encode("password"))
                .fullName("Other Operator")
                .role(UserRole.OPERATOR)
                .organisationName("Other Corp")
                .build());
    }

    protected Application seedApplication(User owner, ApplicationStatus status) {
        return applicationRepository.save(Application.builder()
                .referenceNumber("TEST-" + System.nanoTime())
                .operator(owner)
                .assignedOfficer(officer)
                .status(status)
                .businessName("Test Business")
                .licensingTrack(LicensingTrack.ECDC)
                .businessType("Retail")
                .businessAddress("1 Test Street, Singapore 123456")
                .contactPhone("+65 9999 0000")
                .activityDescription("Test activity description for integration testing")
                .submissionRound(1)
                .build());
    }

    protected Application seedApplicationWithChecklist(User owner) {
        Application app = seedApplication(owner, ApplicationStatus.SITE_VISIT_SCHEDULED);
        checklistItemRepository.saveAll(List.of(
                ChecklistItem.builder().application(app).itemCode("TEST_01")
                        .itemTitle("Fire Safety").sortOrder(1)
                        .status(ChecklistItemStatus.PENDING).build(),
                ChecklistItem.builder().application(app).itemCode("TEST_02")
                        .itemTitle("Electrical Safety").sortOrder(2)
                        .status(ChecklistItemStatus.PENDING).build()
        ));
        return app;
    }

    protected String tokenFor(User user) {
        UserDetails ud = new org.springframework.security.core.userdetails.User(
                user.getEmail(), user.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
        );
        return jwtService.generateToken(ud);
    }

    protected String bearerOf(User user) {
        return "Bearer " + tokenFor(user);
    }

    protected String json(Object obj) throws Exception {
        return objectMapper.writeValueAsString(obj);
    }
}
