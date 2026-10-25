package com.ute.uteexpress.repository;

/** Shared evidence predicate for the identical delivery KPI in Manager and Admin reports. */
public final class OperationalReportRules {
    private OperationalReportRules() { }
    public static final String VALID_PROOF="p.shipment.status=com.ute.uteexpress.enums.ShipmentStatus.DELIVERED "
        +"and p.attempt.status=com.ute.uteexpress.enums.DeliveryAttemptStatus.SUCCESS and p.attempt.startedAt is not null "
        +"and p.attempt.shipment.id=p.shipment.id and p.assignment.id=p.attempt.assignment.id "
        +"and p.assignment.assignmentType=com.ute.uteexpress.enums.AssignmentType.DELIVERY "
        +"and p.employee.id=p.attempt.employee.id and p.actor.id=p.attempt.actor.id and p.deliveredAt>=p.attempt.attemptTime";
}
