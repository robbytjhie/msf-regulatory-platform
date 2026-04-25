package com.regulatory.platform.service;

import com.regulatory.platform.enums.ApplicationStatus;
import com.regulatory.platform.enums.UserRole;
import com.regulatory.platform.exception.InvalidStatusTransitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

@DisplayName("StatusTransitionService — State Machine")
class StatusTransitionServiceTest {

    private final StatusTransitionService service = new StatusTransitionService();

    @ParameterizedTest(name = "Officer: {0} → {1}")
    @MethodSource("validOfficerTransitions")
    @DisplayName("Valid officer transitions should not throw")
    void validOfficerTransitions_shouldSucceed(ApplicationStatus from, ApplicationStatus to) {
        assertThatNoException().isThrownBy(() -> service.validate(from, to, UserRole.OFFICER));
    }

    static Stream<Arguments> validOfficerTransitions() {
        return Stream.of(
                Arguments.of(ApplicationStatus.APPLICATION_RECEIVED,   ApplicationStatus.UNDER_REVIEW),
                Arguments.of(ApplicationStatus.UNDER_REVIEW,           ApplicationStatus.PENDING_PRE_SITE_RESUBMISSION),
                Arguments.of(ApplicationStatus.UNDER_REVIEW,           ApplicationStatus.SITE_VISIT_SCHEDULED),
                Arguments.of(ApplicationStatus.UNDER_REVIEW,           ApplicationStatus.PENDING_APPROVAL),
                Arguments.of(ApplicationStatus.UNDER_REVIEW,           ApplicationStatus.REJECTED),
                Arguments.of(ApplicationStatus.SITE_VISIT_SCHEDULED,   ApplicationStatus.SITE_VISIT_DONE),
                Arguments.of(ApplicationStatus.SITE_VISIT_DONE,        ApplicationStatus.AWAITING_POST_SITE_CLARIFICATION),
                Arguments.of(ApplicationStatus.PENDING_APPROVAL,       ApplicationStatus.APPROVED),
                Arguments.of(ApplicationStatus.PENDING_APPROVAL,       ApplicationStatus.REJECTED)
        );
    }

    @Test
    @DisplayName("Operator resubmits pre-site → PRE_SITE_RESUBMITTED")
    void operatorResubmit_preSite_shouldSucceed() {
        assertThatNoException().isThrownBy(() ->
                service.validate(ApplicationStatus.PENDING_PRE_SITE_RESUBMISSION,
                        ApplicationStatus.PRE_SITE_RESUBMITTED, UserRole.OPERATOR));
    }

    @Test
    @DisplayName("Operator resubmits post-site → POST_SITE_CLARIFICATION_RESUBMITTED")
    void operatorResubmit_postSite_shouldSucceed() {
        assertThatNoException().isThrownBy(() ->
                service.validate(ApplicationStatus.PENDING_POST_SITE_RESUBMISSION,
                        ApplicationStatus.POST_SITE_CLARIFICATION_RESUBMITTED, UserRole.OPERATOR));
    }

    @Test
    @DisplayName("ROLE GATE — Officer cannot trigger operator resubmission")
    void roleGate_officerCannotResubmit() {
        assertThatThrownBy(() ->
                service.validate(ApplicationStatus.PENDING_PRE_SITE_RESUBMISSION,
                        ApplicationStatus.PRE_SITE_RESUBMITTED, UserRole.OFFICER))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("Only operators");
    }

    @Test
    @DisplayName("ROLE GATE — Operator cannot set UNDER_REVIEW")
    void roleGate_operatorCannotSetOfficerStatus() {
        assertThatThrownBy(() ->
                service.validate(ApplicationStatus.APPLICATION_RECEIVED,
                        ApplicationStatus.UNDER_REVIEW, UserRole.OPERATOR))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    @DisplayName("Terminal APPROVED cannot transition anywhere")
    void terminal_approved_noTransitionAllowed() {
        assertThatThrownBy(() ->
                service.validate(ApplicationStatus.APPROVED, ApplicationStatus.UNDER_REVIEW, UserRole.OFFICER))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    @DisplayName("Terminal REJECTED cannot transition anywhere")
    void terminal_rejected_noTransitionAllowed() {
        assertThatThrownBy(() ->
                service.validate(ApplicationStatus.REJECTED, ApplicationStatus.UNDER_REVIEW, UserRole.OFFICER))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    @DisplayName("Cannot skip states: APPLICATION_RECEIVED → SITE_VISIT_SCHEDULED")
    void skipStates_shouldBeRejected() {
        assertThatThrownBy(() ->
                service.validate(ApplicationStatus.APPLICATION_RECEIVED,
                        ApplicationStatus.SITE_VISIT_SCHEDULED, UserRole.OFFICER))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    @DisplayName("SPEC — PENDING_APPROVAL maps to operator label 'Pending Approval'")
    void pendingApproval_operatorLabel_isPendingApproval() {
        assertThat(ApplicationStatus.PENDING_APPROVAL.getOperatorLabel()).isEqualTo("Pending Approval");
    }

    @Test
    @DisplayName("SPEC — APPLICATION_RECEIVED maps to 'Submitted' for operators")
    void applicationReceived_operatorLabel_isSubmitted() {
        assertThat(ApplicationStatus.APPLICATION_RECEIVED.getOperatorLabel()).isEqualTo("Submitted");
    }

    @Test
    @DisplayName("SPEC — Terminal statuses correctly identified")
    void terminal_statuses_identified() {
        assertThat(ApplicationStatus.APPROVED.isTerminal()).isTrue();
        assertThat(ApplicationStatus.REJECTED.isTerminal()).isTrue();
        assertThat(ApplicationStatus.UNDER_REVIEW.isTerminal()).isFalse();
    }
}
