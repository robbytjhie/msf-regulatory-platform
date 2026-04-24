package com.regulatory.platform.repository;

import com.regulatory.platform.entity.ChecklistItem;
import com.regulatory.platform.enums.ChecklistItemStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChecklistItemRepository extends JpaRepository<ChecklistItem, Long> {
    List<ChecklistItem> findByApplicationIdOrderBySortOrderAsc(Long applicationId);

    // UC3 constraint: operator sees ONLY items needing clarification
    List<ChecklistItem> findByApplicationIdAndStatusOrderBySortOrderAsc(
            Long applicationId, ChecklistItemStatus status);
}
