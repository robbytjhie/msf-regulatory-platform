package com.regulatory.platform.service;

import com.regulatory.platform.enums.ApplicationStatus;
import com.regulatory.platform.enums.UserRole;
import com.regulatory.platform.exception.InvalidStatusTransitionException;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

/**
 * Enforces the application status state machine.
 * Only legal transitions are permitted; any other transition throws.
 *
 * Transitions are also role-gated: some transitions are operator-driven
 * (resubmissions), others are officer-driven.
 */
@Service
public class StatusTransitionService {

    // Valid transitions: fromStatus -> Set of allowed toStatuses
    private static final Map<ApplicationStatus, Set<ApplicationStatus>> VALID_TRANSITIONS = Map.ofEntries(
            Map.entry(ApplicationStatus.APPLICATION_RECEIVED,
                    Set.of(ApplicationStatus.UNDER_REVIEW)),

            Map.entry(ApplicationStatus.MANUAL_OFFICER_VALIDATION,
                    Set.of(ApplicationStatus.PENDING_PRE_SITE_RESUBMISSION,
                            ApplicationStatus.SITE_VISIT_SCHEDULED,
                            ApplicationStatus.PENDING_APPROVAL,
                            ApplicationStatus.REJECTED)),

            Map.entry(ApplicationStatus.UNDER_REVIEW,
                    Set.of(ApplicationStatus.PENDING_PRE_SITE_RESUBMISSION,
                            ApplicationStatus.SITE_VISIT_SCHEDULED,
                            ApplicationStatus.PENDING_APPROVAL,
                            ApplicationStatus.REJECTED)),

            Map.entry(ApplicationStatus.PENDING_PRE_SITE_RESUBMISSION,
                    Set.of(ApplicationStatus.PRE_SITE_RESUBMITTED)),

            Map.entry(ApplicationStatus.PRE_SITE_RESUBMITTED,
                    Set.of(ApplicationStatus.UNDER_REVIEW,
                            ApplicationStatus.SITE_VISIT_SCHEDULED)),

            Map.entry(ApplicationStatus.SITE_VISIT_SCHEDULED,
                    Set.of(ApplicationStatus.SITE_VISIT_DONE,
                            ApplicationStatus.AWAITING_POST_SITE_CLARIFICATION,
                            ApplicationStatus.PENDING_APPROVAL)),

            Map.entry(ApplicationStatus.SITE_VISIT_DONE,
                    Set.of(ApplicationStatus.AWAITING_POST_SITE_CLARIFICATION,
                            ApplicationStatus.PENDING_APPROVAL)),

            Map.entry(ApplicationStatus.AWAITING_POST_SITE_CLARIFICATION,
                    Set.of(ApplicationStatus.PENDING_POST_SITE_RESUBMISSION)),

            Map.entry(ApplicationStatus.PENDING_POST_SITE_RESUBMISSION,
                    Set.of(ApplicationStatus.POST_SITE_CLARIFICATION_RESUBMITTED)),

            Map.entry(ApplicationStatus.POST_SITE_CLARIFICATION_RESUBMITTED,
                    Set.of(ApplicationStatus.PENDING_APPROVAL,
                            ApplicationStatus.AWAITING_POST_SITE_CLARIFICATION)),

            Map.entry(ApplicationStatus.PENDING_APPROVAL,
                    Set.of(ApplicationStatus.APPROVED, ApplicationStatus.REJECTED)),

            // Terminal states — no further transitions
            Map.entry(ApplicationStatus.APPROVED, Set.of()),
            Map.entry(ApplicationStatus.REJECTED, Set.of())
    );

    // Transitions that only OPERATORS can trigger
    private static final Set<ApplicationStatus> OPERATOR_TRIGGERED_DESTINATIONS = Set.of(
            ApplicationStatus.PRE_SITE_RESUBMITTED,
            ApplicationStatus.POST_SITE_CLARIFICATION_RESUBMITTED
    );

    /**
     * Validates and confirms the transition is legal for the given role.
     * Throws {@link InvalidStatusTransitionException} if not permitted.
     */
    public void validate(ApplicationStatus from, ApplicationStatus to, UserRole actorRole) {
        Set<ApplicationStatus> allowed = VALID_TRANSITIONS.getOrDefault(from, Set.of());

        if (!allowed.contains(to)) {
            throw new InvalidStatusTransitionException(
                    "Transition from [%s] to [%s] is not permitted".formatted(from, to));
        }

        // Role gate: resubmission transitions are operator-only
        if (OPERATOR_TRIGGERED_DESTINATIONS.contains(to) && actorRole != UserRole.OPERATOR) {
            throw new InvalidStatusTransitionException(
                    "Only operators can trigger the [%s] transition".formatted(to));
        }

        // Officers cannot resubmit on behalf of operators
        if (!OPERATOR_TRIGGERED_DESTINATIONS.contains(to) && actorRole == UserRole.OPERATOR) {
            throw new InvalidStatusTransitionException(
                    "Operators cannot trigger the [%s] transition".formatted(to));
        }
    }
}
