package com.ute.uteexpress.dto.operations;

import com.ute.uteexpress.enums.ShipmentStatus;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.Page;

public final class OperationsDashboardViews {
    private OperationsDashboardViews() { }
    public enum Period { TODAY,LAST_7_DAYS,LAST_30_DAYS,CUSTOM }
    public enum WorkGroup { CONFIRM,PICKUP,INTAKE,DISPATCH,DELIVERY,FAILED,RETURNS }
    public record Range(LocalDate fromDate,LocalDate toDate,LocalDateTime from,LocalDateTime to) { }
    public record Bar(String label,long value,int percent) { }
    public record Work(Long id,String code,String state,LocalDateTime at,String path) { }
    public record Queue(WorkGroup group,String label,long count,Long overThreshold) { }
    public record DurationMetric(String label,Double averageSeconds,long samples) { }
    public record AttemptResults(long successful,long failed,long failedShipments) { }
    public record Dashboard(Long branchId,String branchName,Range range,ShipmentStatus selectedStatus,long originTotal,long destinationCohort,
        long sourceIntakes,long atDestination,long outForDelivery,long activeAssignments,long held,long unreceivedDeparted,long deliverySuccess,long failedShipments,long failedAttempts,BigDecimal deliverySuccessRate,
        long returnRequests,long returned,long originDeliveredResults,BigDecimal returnOutcomeRate,long awaitingReceipt,long openDiscrepancies,
        long openUnassignedTickets,long assignedToMeTickets,long waitingCustomerTickets,long overThresholdTickets,Long warningAgeSeconds,
        List<Bar> daily,List<Bar> statuses,List<Bar> deliveryResults,List<Bar> failureReasons,List<Bar> returns,List<Bar> tickets,
        List<DurationMetric> durations,List<Queue> queues,WorkGroup selectedWork,Page<Work> work,List<Work> trips) { }
}
