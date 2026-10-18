package com.ute.uteexpress.dto.manager;

import com.ute.uteexpress.dto.shipment.ShipmentDetailView;
import com.ute.uteexpress.enums.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;

/** Internal views; never reused by Public Tracking. */
public final class ManagerViews {
    private ManagerViews() { }
    public record BranchOption(Long id, String name) { }
    public record Dashboard(long total, long created, long confirmed, long pickupAssigned, long atOriginBranch) { }
    public record Row(Long id, String trackingNumber, ShipmentStatus status, ShipmentMethod method,
        Long originBranchId, String originBranch, LocalDateTime createdAt) { }
    public record EmployeeOption(Long id, String code, String name) { }
    public record Assignment(Long id, AssignmentType type, AssignmentStatus status, Long employeeId,
        String employeeName, String assignedBy, LocalDateTime assignedAt, LocalDateTime completedAt, String notes) { }
    public record Receipt(String reference, String employeeName, String actorName, LocalDateTime receivedAt, String notes) { }
    public record Detail(Row shipment, Long version, ShipmentDetailView.Contact sender, ShipmentDetailView.Contact receiver,
        String destinationBranch, String packageDescription, BigDecimal weightKg, BigDecimal shippingFee,
        BigDecimal pickupFee, BigDecimal insuranceFee, BigDecimal totalFee,
        Page<ShipmentDetailView.EventView> events, Page<Assignment> assignments, Page<EmployeeOption> candidates,
        Receipt receipt, boolean canConfirm, boolean canCancel, boolean canAssign, boolean canReassign,
        Long activePickupId, boolean canReceive) { }
}
