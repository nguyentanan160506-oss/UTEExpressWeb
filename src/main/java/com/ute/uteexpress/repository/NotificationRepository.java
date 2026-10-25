package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.Notification;
import java.time.LocalDateTime;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    String VISIBLE = " n.user.id = :userId and n.user.status = com.ute.uteexpress.enums.UserStatus.ACTIVE and ("
        + "(n.sourceEvent is null and n.sourceTicketEvent is null and n.relatedTicket is null and n.relatedShipment is null) or "
        + "((n.audience = 'USER' or n.audience is null) and n.relatedTicket is null and exists (select s.id from Shipment s where s.id = n.relatedShipment.id and s.sender.id = :userId) "
        + "and exists (select r.id from UserRole r where r.user.id = :userId and r.role.name = 'ROLE_USER')) or "
        + "(n.audience = 'SHIPPER' and n.relatedTicket is null and exists (select a.id from ShipmentAssignment a join a.employee e where a.id = n.assignment.id and e.user.id = :userId "
        + "and e.status = com.ute.uteexpress.enums.EmployeeStatus.ACTIVE and e.position = com.ute.uteexpress.enums.EmployeePosition.COURIER_DRIVER "
        + "and e.branch.id = n.scopeBranch.id and e.branch.status = com.ute.uteexpress.enums.BranchStatus.ACTIVE) "
        + "and exists (select r.id from UserRole r where r.user.id = :userId and r.role.name = 'ROLE_SHIPPER')) or "
        + "(n.audience = 'MANAGER' and exists (select m.id from ManagerBranch m where m.manager.id = :userId and m.branch.id = n.scopeBranch.id "
        + "and m.branch.status = com.ute.uteexpress.enums.BranchStatus.ACTIVE) "
        + "and exists (select r.id from UserRole r where r.user.id = :userId and r.role.name = 'ROLE_MANAGER') "
        + "and (n.relatedTicket is null or (exists (select t.id from SupportTicket t where t.id=n.relatedTicket.id and t.handlingBranch.id=n.scopeBranch.id) and exists (select e.id from Employee e where e.user.id=:userId "
        + "and e.status=com.ute.uteexpress.enums.EmployeeStatus.ACTIVE and e.branch.id=n.scopeBranch.id)))) or "
        + "(n.audience='USER' and exists (select t.id from SupportTicket t where t.id=n.relatedTicket.id and t.user.id=:userId and t.shipment.sender.id=:userId and t.handlingBranch is not null) "
        + "and exists (select r.id from UserRole r where r.user.id=:userId and r.role.name='ROLE_USER')))";
    @Query("select n from Notification n where " + VISIBLE + " and (:unread = false or n.isRead = false) order by n.createdAt desc, n.id desc")
    Page<Notification> findVisible(Long userId, boolean unread, Pageable page);
    @Query("select count(n) from Notification n where " + VISIBLE + " and n.isRead = false")
    long unreadCount(Long userId);
    @Query("select n from Notification n where n.id = :id and " + VISIBLE)
    Optional<Notification> findVisibleById(Long id, Long userId);
    @Modifying @Query("update Notification n set n.isRead = true, n.readAt = :now where n.id = :id and " + VISIBLE + " and n.isRead = false")
    int markRead(Long id, Long userId, LocalDateTime now);
    @Modifying @Query("update Notification n set n.isRead = true, n.readAt = :now where " + VISIBLE + " and n.isRead = false")
    int markAllRead(Long userId, LocalDateTime now);
    boolean existsByUserIdAndSourceTicketEventIdAndAudience(Long userId,Long eventId,String audience);
    boolean existsByUserIdAndSourceEventIdAndAudience(Long userId, Long eventId, String audience);
    @Query("select n.id from Notification n where (n.sourceEvent is not null or n.sourceTicketEvent is not null) and n.deliveredAt is null and (n.deliveryRetryAt is null or n.deliveryRetryAt <= :now) order by n.id")
    List<Long> findDeliveryWork(LocalDateTime now, Pageable page);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select n from Notification n where n.id = :id")
    Optional<Notification> lockDelivery(Long id);
}
