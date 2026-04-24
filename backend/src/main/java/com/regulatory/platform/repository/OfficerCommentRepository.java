package com.regulatory.platform.repository;

import com.regulatory.platform.entity.OfficerComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OfficerCommentRepository extends JpaRepository<OfficerComment, Long> {
    List<OfficerComment> findByApplicationIdOrderByCreatedAtDesc(Long applicationId);
    List<OfficerComment> findByApplicationIdAndSubmissionRound(Long applicationId, int round);
    List<OfficerComment> findByApplicationIdAndResolved(Long applicationId, boolean resolved);
}
