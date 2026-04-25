package com.regulatory.platform.repository;

import com.regulatory.platform.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByApplicationIdOrderByCreatedAtDesc(Long applicationId);
    List<Document> findByApplicationIdAndSubmissionRound(Long applicationId, int round);

    @Query("""
            SELECT d FROM Document d
            JOIN FETCH d.application a
            JOIN FETCH a.operator
            WHERE d.id = :id
            """)
    Optional<Document> findByIdWithApplicationAndOperator(@Param("id") Long id);
}
