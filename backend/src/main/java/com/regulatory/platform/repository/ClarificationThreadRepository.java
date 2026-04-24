package com.regulatory.platform.repository;

import com.regulatory.platform.entity.ClarificationThread;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClarificationThreadRepository extends JpaRepository<ClarificationThread, Long> {
    List<ClarificationThread> findByChecklistItemIdOrderByCreatedAtAsc(Long checklistItemId);
}
