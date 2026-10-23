package com.ute.uteexpress.service.notifications;

import com.ute.uteexpress.entity.ShipmentEvent;
import com.ute.uteexpress.enums.ShipmentStatus;
import java.util.*;

/** Exact persisted title + status whitelist. Never copy free-form event descriptions. */
public final class ShipmentNotificationPolicy {
    private ShipmentNotificationPolicy() { }
    private static final Map<String, ShipmentStatus> USER = Map.ofEntries(
        Map.entry("Đã tạo vận đơn", ShipmentStatus.CREATED),
        Map.entry("Trạng thái: Đã xác nhận", ShipmentStatus.CONFIRMED),
        Map.entry("Trạng thái: Đã hủy", ShipmentStatus.CANCELLED),
        Map.entry("Shipper đã thực nhận kiện", ShipmentStatus.PICKED_UP),
        Map.entry("Chi nhánh nguồn đã tiếp nhận kiện", ShipmentStatus.AT_ORIGIN_BRANCH),
        Map.entry("Chi nhánh nhận bàn giao HOME_PICKUP", ShipmentStatus.AT_ORIGIN_BRANCH),
        Map.entry("Kiện đã xuất chuyến", ShipmentStatus.IN_TRANSIT),
        Map.entry("Kiện hoàn đã xuất chuyến", ShipmentStatus.RETURNING),
        Map.entry("Phân loại chờ giao tại cùng chi nhánh", ShipmentStatus.AT_DESTINATION_BRANCH),
        Map.entry("Shipper bắt đầu lần giao", ShipmentStatus.OUT_FOR_DELIVERY),
        Map.entry("Lần giao chưa thành công", ShipmentStatus.DELIVERY_FAILED),
        Map.entry("Manager cho phép giao lại", ShipmentStatus.DELIVERY_FAILED),
        Map.entry("Đã giao kiện cho người nhận", ShipmentStatus.DELIVERED),
        Map.entry("Manager khởi tạo hoàn, chờ thu hồi/vận chuyển thực tế", ShipmentStatus.RETURNING),
        Map.entry("Đã trả kiện thực tế cho người gửi/đại diện", ShipmentStatus.RETURNED));
    public static String userTitle(ShipmentEvent e) {
        String title = e.getEventTitle();
        if ("Đã kiểm nhận kiện tại điểm đến".equals(title) && Set.of(ShipmentStatus.AT_ORIGIN_HUB,
                ShipmentStatus.AT_DESTINATION_HUB, ShipmentStatus.AT_DESTINATION_BRANCH, ShipmentStatus.RETURNING).contains(e.getToStatus())) {
            return e.getToStatus() == ShipmentStatus.RETURNING
                ? (e.getBranch() != null && e.getBranch().getId().equals(e.getShipment().getOriginBranch().getId())
                    ? "Kiện hoàn đã được kiểm nhận tại chi nhánh nguồn" : "Kiện hoàn đã được kiểm nhận tại điểm đến")
                : "Kiện đã được kiểm nhận tại điểm đến";
        }
        if (title == null || USER.get(title) != e.getToStatus()) { return null; }
        return switch (title) {
            case "Manager cho phép giao lại" -> "Đã có kế hoạch giao lại";
            case "Manager khởi tạo hoàn, chờ thu hồi/vận chuyển thực tế" -> "Đã khởi tạo hoàn hàng, chờ xử lý thực tế";
            case "Shipper đã thực nhận kiện" -> "Đã lấy hàng thành công";
            default -> title;
        };
    }
    public static boolean assignment(ShipmentEvent e) {
        return switch (Objects.toString(e.getEventTitle(), "")) {
            case "Đã phân công lấy hàng", "Đã phân công lại lấy hàng" -> e.getToStatus() == ShipmentStatus.PICKUP_ASSIGNED;
            case "Phân công giao hàng", "Phân công lại giao hàng" -> e.getToStatus() == e.getFromStatus()
                && Set.of(ShipmentStatus.AT_DESTINATION_BRANCH, ShipmentStatus.DELIVERY_FAILED).contains(e.getToStatus());
            case "Manager cho phép giao lại" -> e.getToStatus() == ShipmentStatus.DELIVERY_FAILED;
            case "Manager khởi tạo hoàn, chờ thu hồi/vận chuyển thực tế" -> e.getToStatus() == ShipmentStatus.RETURNING;
            case "Chi nhánh từ chối bàn giao" -> e.getToStatus() == ShipmentStatus.PICKED_UP && e.getFromStatus() == e.getToStatus();
            case "Chi nhánh nhận bàn giao HOME_PICKUP" -> e.getToStatus() == ShipmentStatus.AT_ORIGIN_BRANCH && e.getFromStatus() == ShipmentStatus.PICKED_UP;
            case "Chi nhánh đã thực nhận lại kiện", "Chi nhánh từ chối nhận lại kiện" -> e.getFromStatus() == e.getToStatus()
                && Set.of(ShipmentStatus.DELIVERY_FAILED, ShipmentStatus.RETURNING).contains(e.getToStatus());
            default -> false;
        };
    }
    public static String managerTitle(ShipmentEvent e) {
        return switch (Objects.toString(e.getEventTitle(), "")) {
            case "Đã tạo vận đơn" -> e.getToStatus() == ShipmentStatus.CREATED ? "Có yêu cầu gửi hàng cần xem xét" : null;
            case "Lấy hàng chưa thành công" -> e.getToStatus() == ShipmentStatus.PICKUP_ASSIGNED ? "Lấy hàng chưa thành công, cần xem xét" : null;
            case "Lần giao chưa thành công" -> e.getToStatus() == ShipmentStatus.DELIVERY_FAILED ? "Giao chưa thành công, cần quyết định xử lý" : null;
            case "Ghi nhận sai lệch kiểm đếm" -> e.getFromStatus() == e.getToStatus() ? "Có sai lệch kiểm đếm cần xử lý" : null;
            case "Yêu cầu bàn giao chi nhánh nguồn", "Shipper yêu cầu bàn giao trả lại chi nhánh" -> "Có yêu cầu bàn giao cần kiểm nhận";
            case "Đã kiểm nhận kiện tại điểm đến" -> e.getToStatus() == ShipmentStatus.RETURNING ? "Đã kiểm nhận kiện hoàn, cần tiếp tục xử lý" : null;
            default -> null;
        };
    }
}
