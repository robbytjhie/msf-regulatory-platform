package com.regulatory.platform.repository;

import com.regulatory.platform.entity.Notification;
import com.regulatory.platform.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserOrderByCreatedAtDesc(User user);
}
