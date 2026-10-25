package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.SupportTicketAssignment;
import java.util.List;
import org.springframework.data.jpa.repository.*;
public interface SupportTicketAssignmentRepository extends JpaRepository<SupportTicketAssignment,Long> {
    List<SupportTicketAssignment> findByTicketIdAndEndedAtIsNull(Long ticketId);
}
