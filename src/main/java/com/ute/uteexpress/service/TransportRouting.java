package com.ute.uteexpress.service;

import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import java.util.*;

/** Pure routing over the shipment's hub snapshots; never selects an arbitrary hub. */
public final class TransportRouting {
    private TransportRouting() { }
    public static List<Branch> path(Shipment s) {
        require(s.getOriginBranch(), BranchType.LOCAL_BRANCH); require(s.getDestinationBranch(), BranchType.LOCAL_BRANCH);
        require(s.getOriginHub(), BranchType.CENTRAL_HUB); require(s.getDestinationHub(), BranchType.CENTRAL_HUB);
        if (same(s.getOriginBranch(), s.getDestinationBranch())) {
            if (!same(s.getOriginHub(), s.getDestinationHub())) { throw rule(); }
            return List.of(s.getOriginBranch());
        }
        return same(s.getOriginHub(), s.getDestinationHub())
            ? List.of(s.getOriginBranch(), s.getOriginHub(), s.getDestinationBranch())
            : List.of(s.getOriginBranch(), s.getOriginHub(), s.getDestinationHub(), s.getDestinationBranch());
    }
    public static void validateLinks(Shipment s) {
        path(s);
        if (!same(s.getOriginBranch().getParentHub(), s.getOriginHub())
                || !same(s.getDestinationBranch().getParentHub(), s.getDestinationHub())) { throw rule(); }
    }
    public static void validateRoute(Branch from, Branch to, TransportLeg leg) {
        if (same(from, to) || leg == null) { throw rule(); }
        switch (leg) {
            case COLLECTION -> {
                require(from, BranchType.LOCAL_BRANCH); require(to, BranchType.CENTRAL_HUB);
                if (!same(from.getParentHub(), to)) { throw rule(); }
            }
            case LINEHAUL -> { require(from, BranchType.CENTRAL_HUB); require(to, BranchType.CENTRAL_HUB); }
            case DISTRIBUTION -> {
                require(from, BranchType.CENTRAL_HUB); require(to, BranchType.LOCAL_BRANCH);
                if (!same(to.getParentHub(), from)) { throw rule(); }
            }
        }
    }
    public static void requireNext(Shipment s, TransportTrip t) {
        validateLinks(s); List<Branch> path = path(s);
        for (int i = 0; i < path.size() - 1; i++) {
            if (same(path.get(i), t.getOrigin()) && same(path.get(i + 1), t.getDestination())
                    && same(s.getCurrentBranch(), t.getOrigin()) && s.getStatus() == statusAt(s, t.getOrigin())) { return; }
        }
        throw new LogisticsRuleException("Kiện chưa ở đúng điểm xuất phát hoặc chuyến không phải chặng tiếp theo.");
    }
    public static List<Branch> returnPath(Shipment s) {
        var reversed = new ArrayList<>(path(s)); Collections.reverse(reversed); return List.copyOf(reversed);
    }
    public static void requireReturnNext(Shipment s, TransportTrip t, ReturnRequest request) {
        validateLinks(s);
        if (request == null || !s.getId().equals(request.getShipment().getId()) || request.getStage() != ReturnStage.READY_FOR_TRANSPORT
                || s.getStatus() != ShipmentStatus.RETURNING || !same(s.getCurrentBranch(), t.getOrigin())) { throw rule(); }
        var path = returnPath(s);
        for (int i = 0; i < path.size() - 1; i++) {
            if (same(path.get(i), t.getOrigin()) && same(path.get(i + 1), t.getDestination())) { return; }
        }
        throw rule();
    }
    public static void requireReturnReceipt(Shipment s, TransportTrip t, ReturnRequest request) {
        if (!s.getId().equals(request.getShipment().getId()) || request.getStage() != ReturnStage.IN_TRANSIT) { throw rule(); }
        var path = returnPath(s);
        for (int i = 0; i < path.size() - 1; i++) {
            if (same(path.get(i), t.getOrigin()) && same(path.get(i + 1), t.getDestination())) { return; }
        }
        throw rule();
    }
    public static ShipmentStatus statusAt(Shipment s, Branch point) {
        if (same(point, s.getDestinationBranch())) { return ShipmentStatus.AT_DESTINATION_BRANCH; }
        if (same(point, s.getOriginBranch())) { return ShipmentStatus.AT_ORIGIN_BRANCH; }
        if (same(point, s.getDestinationHub())) { return ShipmentStatus.AT_DESTINATION_HUB; }
        if (same(point, s.getOriginHub())) { return ShipmentStatus.AT_ORIGIN_HUB; }
        throw rule();
    }
    public static ShipmentStatus receiptStatus(Shipment s, TransportTrip t) {
        var path = path(s);
        for (int i = 0; i < path.size() - 1; i++) {
            if (same(path.get(i), t.getOrigin()) && same(path.get(i + 1), t.getDestination())) { return statusAt(s, t.getDestination()); }
        }
        throw rule();
    }
    public static boolean same(Branch a, Branch b) { return a != null && b != null && a.getId() != null && a.getId().equals(b.getId()); }
    private static void require(Branch b, BranchType type) {
        if (b == null || b.getId() == null || b.getStatus() != BranchStatus.ACTIVE || b.getBranchType() != type) { throw rule(); }
    }
    private static LogisticsRuleException rule() { return new LogisticsRuleException("Tuyến hoặc liên kết chi nhánh/hub chưa hợp lệ; cần cấu hình dữ liệu đúng trước khi vận chuyển."); }
}
