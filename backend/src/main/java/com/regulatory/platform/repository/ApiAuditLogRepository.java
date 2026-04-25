package com.regulatory.platform.repository;

import com.regulatory.platform.entity.ApiAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiAuditLogRepository extends JpaRepository<ApiAuditLog, Long> {
}
