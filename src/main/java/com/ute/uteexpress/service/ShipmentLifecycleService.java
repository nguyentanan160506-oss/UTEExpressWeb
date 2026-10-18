package com.ute.uteexpress.service;

import com.ute.uteexpress.dto.shipment.*;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.ShipmentStatus;
import com.ute.uteexpress.repository.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.*;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

@Service @Validated
public class ShipmentLifecycleService {
    private final ShipmentRepository shipments;
    private final ShipmentEventRepository events;
    private final ShipmentAssignmentRepository assignments;
    private final LogisticsAccessService access;
    private final Clock clock;
    public ShipmentLifecycleService(ShipmentRepository shipments, ShipmentEventRepository events,
            ShipmentAssignmentRepository assignments, LogisticsAccessService access, Clock clock) {
        this.shipments = shipments; this.events = events; this.assignments = assignments;
        this.access = access; this.clock = clock;
    }

    @Transactional
    @PreAuthorize("hasRole('MANAGER')")
    public Long transition(@NotNull @Valid ShipmentTransitionCommand command) {
        Shipment shipment = shipments.findForLifecycle(command.shipmentId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy vận đơn."));
        User actor = access.requireManagerForBranch(shipment.getOriginBranch().getId());
        if (command.expectedVersion() == null || !Objects.equals(shipment.getVersion(), command.expectedVersion())) {
            throw new ObjectOptimisticLockingFailureException(Shipment.class, command.shipmentId());
        }
        ShipmentStatus from = shipment.getStatus(), to = command.targetStatus();
        if (!ShipmentTransitionPolicy.isPotentialEdge(from, to)) {
            throw new LogisticsRuleException("Chuyển trạng thái không hợp lệ.");
        }
        if (!ShipmentTransitionPolicy.isEnabled(from, to)) {
            throw new LogisticsRuleException("Thao tác cần phân công, bàn giao hoặc chuyến được triển khai trong Commit 10–13.");
        }
        if (to == ShipmentStatus.CANCELLED
                && assignments.existsByShipmentIdAndStatusIn(shipment.getId(), LogisticsAccessService.ACTIVE_ASSIGNMENTS)) {
            throw new LogisticsRuleException("Cần xử lý phân công đang hoạt động trước khi hủy vận đơn.");
        }
        shipment.setStatus(to);
        shipments.saveAndFlush(shipment); // Hibernate checks and increments @Version in the UPDATE predicate.
        ShipmentEvent event = new ShipmentEvent();
        event.setShipment(shipment); event.setActor(actor); event.setBranch(shipment.getOriginBranch());
        event.setFromStatus(from); event.setToStatus(to);
        event.setEventTitle("Trạng thái: " + ShipmentStatusLabels.VALUES.get(to));
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        event.setOccurredAt(now.withNano(now.getNano() / 100 * 100));
        events.saveAndFlush(event); // Any failure rolls back the shipment update as well.
        return shipment.getVersion();
    }
}
