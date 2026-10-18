package com.ute.uteexpress.dto.shipment;

import com.ute.uteexpress.enums.ShipmentStatus;
import java.util.Map;
import static java.util.Map.entry;

public final class ShipmentStatusLabels {
    private ShipmentStatusLabels() { }
    public static final Map<ShipmentStatus, String> VALUES = Map.ofEntries(
        entry(ShipmentStatus.CREATED, "Đã tạo"), entry(ShipmentStatus.CONFIRMED, "Đã xác nhận"),
        entry(ShipmentStatus.PICKUP_ASSIGNED, "Đã phân công lấy hàng"), entry(ShipmentStatus.PICKED_UP, "Đã lấy hàng"),
        entry(ShipmentStatus.AT_ORIGIN_BRANCH, "Chi nhánh nguồn đã nhận hàng"),
        entry(ShipmentStatus.AT_DESTINATION_BRANCH, "Chi nhánh đích đã nhận hàng"),
        entry(ShipmentStatus.AT_ORIGIN_HUB, "Tại kho gửi"), entry(ShipmentStatus.IN_TRANSIT, "Đang vận chuyển"),
        entry(ShipmentStatus.AT_DESTINATION_HUB, "Tại kho nhận"), entry(ShipmentStatus.OUT_FOR_DELIVERY, "Đang giao hàng"),
        entry(ShipmentStatus.DELIVERED, "Đã giao"), entry(ShipmentStatus.DELIVERY_FAILED, "Giao không thành công"),
        entry(ShipmentStatus.RETURNING, "Đang hoàn hàng"), entry(ShipmentStatus.RETURNED, "Đã hoàn hàng"),
        entry(ShipmentStatus.CANCELLED, "Đã hủy"));
}
