package com.regulatory.platform.config;

import com.regulatory.platform.entity.Application;
import com.regulatory.platform.entity.StatusHistory;
import com.regulatory.platform.entity.User;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataSeederTest {

    @Mock private UserRepository userRepository;
    @Mock private ApiAuditLogRepository apiAuditLogRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private ApplicationRepository applicationRepository;
    @Mock private DocumentRepository documentRepository;
    @Mock private ApplicationRoundSnapshotRepository applicationRoundSnapshotRepository;
    @Mock private OfficerCommentRepository officerCommentRepository;
    @Mock private StatusHistoryRepository statusHistoryRepository;
    @Mock private ChecklistItemRepository checklistItemRepository;
    @Mock private ClarificationThreadRepository clarificationThreadRepository;
    @Mock private PasswordEncoder passwordEncoder;

    private DataSeeder dataSeeder;

    @BeforeEach
    void setUp() {
        dataSeeder = new DataSeeder();
    }

    @Test
    void doesNothingWhenSeedingDisabled() throws Exception {
        ReflectionTestUtils.setField(dataSeeder, "seedEnabled", false);
        ReflectionTestUtils.setField(dataSeeder, "resetOnStartup", false);

        CommandLineRunner runner = dataSeeder.seedData(
                userRepository,
                apiAuditLogRepository,
                notificationRepository,
                applicationRepository,
                documentRepository,
                applicationRoundSnapshotRepository,
                officerCommentRepository,
                statusHistoryRepository,
                checklistItemRepository,
                clarificationThreadRepository,
                passwordEncoder
        );
        runner.run();

        verifyNoInteractions(userRepository, applicationRepository, statusHistoryRepository, passwordEncoder);
    }

    @Test
    void skipsSeedingWhenDataExistsAndResetDisabled() throws Exception {
        ReflectionTestUtils.setField(dataSeeder, "seedEnabled", true);
        ReflectionTestUtils.setField(dataSeeder, "resetOnStartup", false);
        when(userRepository.count()).thenReturn(2L);

        CommandLineRunner runner = dataSeeder.seedData(
                userRepository,
                apiAuditLogRepository,
                notificationRepository,
                applicationRepository,
                documentRepository,
                applicationRoundSnapshotRepository,
                officerCommentRepository,
                statusHistoryRepository,
                checklistItemRepository,
                clarificationThreadRepository,
                passwordEncoder
        );
        runner.run();

        verify(userRepository, times(1)).count();
        verify(userRepository, never()).save(any(User.class));
        verify(applicationRepository, never()).save(any(Application.class));
    }

    @Test
    void reseedsWhenResetEnabled() throws Exception {
        ReflectionTestUtils.setField(dataSeeder, "seedEnabled", true);
        ReflectionTestUtils.setField(dataSeeder, "resetOnStartup", true);
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(applicationRepository.save(any(Application.class))).thenAnswer(i -> i.getArgument(0));
        when(statusHistoryRepository.save(any(StatusHistory.class))).thenAnswer(i -> i.getArgument(0));

        CommandLineRunner runner = dataSeeder.seedData(
                userRepository,
                apiAuditLogRepository,
                notificationRepository,
                applicationRepository,
                documentRepository,
                applicationRoundSnapshotRepository,
                officerCommentRepository,
                statusHistoryRepository,
                checklistItemRepository,
                clarificationThreadRepository,
                passwordEncoder
        );
        runner.run();

        verify(userRepository, times(1)).deleteAllInBatch();
        verify(applicationRepository, times(1)).deleteAllInBatch();
        verify(applicationRoundSnapshotRepository, times(1)).deleteAllInBatch();
        verify(userRepository, times(3)).save(any(User.class));
        verify(applicationRepository, times(4)).save(any(Application.class));
    }
}
