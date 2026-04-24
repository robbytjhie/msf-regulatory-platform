package com.regulatory.platform.repository;

import com.regulatory.platform.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByApplicationIdOrderByCreatedAtDesc(Long applicationId);
    List<Document> findByApplicationIdAndSubmissionRound(Long applicationId, int round);
}
