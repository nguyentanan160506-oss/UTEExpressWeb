package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface SupportTicketRepository extends JpaRepository<SupportTicket,Long> {
    String SCOPE=" exists (select m.id from ManagerBranch m where m.manager.id=:actorId and m.branch.id=t.handlingBranch.id and m.branch.status=com.ute.uteexpress.enums.BranchStatus.ACTIVE)";
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from SupportTicket t where t.id=:id and "+SCOPE)
    Optional<SupportTicket> lockManaged(Long id,Long actorId);
    @EntityGraph(attributePaths={"handlingBranch","user","shipment","assignedManager"})
    @Query("select t from SupportTicket t where t.id=:id and "+SCOPE)
    Optional<SupportTicket> findManaged(Long id,Long actorId);
    @EntityGraph(attributePaths={"handlingBranch","user","shipment","assignedManager"})
    @Query("select t from SupportTicket t where t.handlingBranch.id=:branchId and "+SCOPE
        +" and (:status is null or t.status=:status) and (:priority is null or t.priority=:priority) and (:category is null or t.category=:category) "
        +"and (:assignee is null or t.assignedManager.id=:assignee) and (:unassigned=false or t.assignedManager is null) "
        +"and (:overdue=false or (t.warningDueAt < :now and t.status not in (com.ute.uteexpress.enums.TicketStatus.RESOLVED,com.ute.uteexpress.enums.TicketStatus.CLOSED)))")
    Page<SupportTicket> search(Long actorId,Long branchId,TicketStatus status,TicketPriority priority,TicketCategory category,
        Long assignee,boolean unassigned,boolean overdue,LocalDateTime now,Pageable page);
    Optional<SupportTicket> findByRequestTokenAndCreatedById(String token,Long creatorId);
    @EntityGraph(attributePaths={"shipment"})
    @Query("select t from SupportTicket t where t.id=:id and t.user.id=:owner and t.shipment.sender.id=:owner and t.handlingBranch is not null")
    Optional<SupportTicket> findOwnedForCustomer(Long id,Long owner);
}
