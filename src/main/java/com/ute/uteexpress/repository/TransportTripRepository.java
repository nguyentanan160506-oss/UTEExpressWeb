package com.ute.uteexpress.repository;

import com.ute.uteexpress.entity.TransportTrip;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface TransportTripRepository extends JpaRepository<TransportTrip, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select t from TransportTrip t where t.id = :id")
    Optional<TransportTrip> findForUpdate(Long id);
    Optional<TransportTrip> findByRequestTokenAndCreatedById(String requestToken, Long userId);
    String SCOPE = """
         exists (select m.id from ManagerBranch m where m.manager.id = :managerId
         and m.branch.status = com.ute.uteexpress.enums.BranchStatus.ACTIVE
         and (m.branch.id = t.origin.id or m.branch.id = t.destination.id))
        """;
    @Query(value = "select t from TransportTrip t where " + SCOPE, countQuery = "select count(t) from TransportTrip t where " + SCOPE)
    Page<TransportTrip> findManaged(Long managerId, Pageable pageable);
    @Query("select t from TransportTrip t where t.id = :id and " + SCOPE)
    Optional<TransportTrip> findManagedById(Long id, Long managerId);
}
