package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
}
