package com.regulatory.platform.repository;

import com.regulatory.platform.entity.ApplicationRoundSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApplicationRoundSnapshotRepository extends JpaRepository<ApplicationRoundSnapshot, Long> {
    List<ApplicationRoundSnapshot> findByApplicationIdOrderBySubmissionRoundAsc(Long applicationId);
}
