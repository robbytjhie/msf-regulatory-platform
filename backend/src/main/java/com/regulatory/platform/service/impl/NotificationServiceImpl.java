package com.regulatory.platform.service.impl;

import com.regulatory.platform.dto.response.NotificationResponse;
import com.regulatory.platform.entity.Application;
import com.regulatory.platform.entity.Notification;
import com.regulatory.platform.entity.User;
import com.regulatory.platform.enums.ApplicationStatus;
import com.regulatory.platform.enums.NotificationType;
import com.regulatory.platform.enums.UserRole;
import com.regulatory.platform.repository.NotificationRepository;
import com.regulatory.platform.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final JavaMailSender mailSender;

    private static final String OFFICER_EMAIL = "robby.tjhie@gmail.com";
    private static final String OPERATOR_EMAIL = "robby.tjhie08@gmail.com";

    @Override
    public void create(User recipient, Application application, NotificationType type, String message) {
        Notification notification = Notification.builder()
                .user(recipient)
                .application(application)
                .type(type)
                .message(message)
                .read(false)
                .build();
        notificationRepository.save(notification);
        log.info("Notification created for {} on {}: {}", recipient.getEmail(), application.getReferenceNumber(), type);
        sendEmailNotification(recipient, application, type, message);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationResponse> getForUser(User user) {
        return notificationRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(NotificationResponse::from)
                .toList();
    }

    @Override
    public void markAllRead(User user) {
        notificationRepository.findByUserOrderByCreatedAtDesc(user)
                .forEach(n -> n.setRead(true));
    }

    private void sendEmailNotification(User recipient, Application application, NotificationType type, String message) {
        String targetEmail = resolveTargetEmail(recipient);
        EmailContent content = buildEmailContent(recipient, application, type, message);
        SimpleMailMessage email = new SimpleMailMessage();
        email.setTo(targetEmail);
        email.setSubject(content.subject());
        email.setText(content.body());
        try {
            mailSender.send(email);
            log.info("Email notification sent to {} for application {}", targetEmail, application.getReferenceNumber());
        } catch (Exception ex) {
            // Keep in-app notification flow resilient even if SMTP is unavailable.
            log.warn("Failed to send email notification to {} for {}: {}",
                    targetEmail, application.getReferenceNumber(), ex.getMessage());
        }
    }

    private String resolveTargetEmail(User recipient) {
        if (recipient.getRole() == UserRole.OFFICER) {
            return OFFICER_EMAIL;
        }
        if (recipient.getRole() == UserRole.OPERATOR) {
            return OPERATOR_EMAIL;
        }
        return recipient.getEmail();
    }

    private EmailContent buildEmailContent(User recipient, Application application, NotificationType type, String message) {
        String ref = application.getReferenceNumber() == null ? "-" : application.getReferenceNumber();
        String businessName = application.getBusinessName() == null ? "-" : application.getBusinessName();
        String operatorName = recipient.getFullName() == null ? "Operator" : recipient.getFullName();
        if (type == NotificationType.STATUS_CHANGE) {
            return statusChangeEmail(operatorName, application.getStatus(), ref, businessName, message);
        }
        String subject = "[MSF Licensing] " + type + " - " + ref;
        String body = """
                Hello %s,

                Notification Type: %s
                Application: %s
                Business Name: %s

                %s
                """.formatted(operatorName, type, ref, businessName, message);
        return new EmailContent(subject, body);
    }

    private EmailContent statusChangeEmail(String operatorName,
                                           ApplicationStatus status,
                                           String referenceNumber,
                                           String businessName,
                                           String message) {
        if (status == ApplicationStatus.UNDER_REVIEW) {
            return new EmailContent(
                    "Your application is now under review - " + referenceNumber,
                    """
                            Hi %s,

                            Your application %s for %s is now Under Review.
                            Our officer team is evaluating your submission.
                            We will notify you if clarifications or resubmission are needed.
                            """.formatted(operatorName, referenceNumber, businessName)
            );
        }
        if (status == ApplicationStatus.PENDING_PRE_SITE_RESUBMISSION) {
            return new EmailContent(
                    "Action required: Pre-site resubmission needed - " + referenceNumber,
                    """
                            Hi %s,

                            Your application %s requires updates before site review.
                            Status: Pending Pre-Site Resubmission.
                            Please review officer comments, update required documents, and resubmit in the portal.
                            """.formatted(operatorName, referenceNumber)
            );
        }
        if (status == ApplicationStatus.SITE_VISIT_SCHEDULED || status == ApplicationStatus.SITE_VISIT_DONE) {
            return new EmailContent(
                    "Site visit stage started - " + referenceNumber,
                    """
                            Hi %s,

                            Your application %s has progressed to site visit review.
                            Please monitor checklist requests and provide clarifications promptly if requested.
                            """.formatted(operatorName, referenceNumber)
            );
        }
        if (status == ApplicationStatus.AWAITING_POST_SITE_CLARIFICATION) {
            return new EmailContent(
                    "Action required: Post-site clarification needed - " + referenceNumber,
                    """
                            Hi %s,

                            Your application %s is now Awaiting Post-Site Clarification.
                            Please respond to checklist clarification items in the portal so the officer can continue review.
                            """.formatted(operatorName, referenceNumber)
            );
        }
        if (status == ApplicationStatus.PENDING_POST_SITE_RESUBMISSION) {
            return new EmailContent(
                    "Action required: Post-site resubmission needed - " + referenceNumber,
                    """
                            Hi %s,

                            Your application %s now requires Post-Site Resubmission.
                            Please upload revised documents/details and submit again for officer verification.
                            """.formatted(operatorName, referenceNumber)
            );
        }
        if (status == ApplicationStatus.PENDING_APPROVAL) {
            return new EmailContent(
                    "Your application is in final review - " + referenceNumber,
                    """
                            Hi %s,

                            Good news - your application %s is now Pending Approval.
                            Final review is in progress. We will notify you once a decision is made.
                            """.formatted(operatorName, referenceNumber)
            );
        }
        if (status == ApplicationStatus.APPROVED) {
            return new EmailContent(
                    "Application approved - " + referenceNumber,
                    """
                            Hi %s,

                            Congratulations. Your application %s for %s has been Approved.
                            You may proceed with the next regulatory steps as instructed by MSF.
                            """.formatted(operatorName, referenceNumber, businessName)
            );
        }
        if (status == ApplicationStatus.REJECTED) {
            return new EmailContent(
                    "Application decision: Rejected - " + referenceNumber,
                    """
                            Hi %s,

                            Your application %s has been Rejected.
                            Please review officer notes in the portal for details and guidance on next steps.
                            """.formatted(operatorName, referenceNumber)
            );
        }
        return new EmailContent(
                "Application status updated - " + referenceNumber,
                """
                        Hi %s,

                        Your application %s status has been updated.
                        %s
                        """.formatted(operatorName, referenceNumber, message == null ? "" : message)
        );
    }

    private record EmailContent(String subject, String body) {}
}
