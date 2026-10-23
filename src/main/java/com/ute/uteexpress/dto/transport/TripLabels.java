package com.ute.uteexpress.dto.transport;

import com.ute.uteexpress.enums.*;
import java.util.Map;

public final class TripLabels {
    private TripLabels() { }
    public static final Map<TripStatus, String> STATUS = Map.of(TripStatus.DRAFT, "Đang lập manifest", TripStatus.SEALED, "Đã niêm phong",
        TripStatus.DEPARTED, "Đã xuất phát", TripStatus.RECONCILING, "Đã đến, đang kiểm đếm", TripStatus.CLOSED, "Đã đóng chuyến");
    public static final Map<TransportLeg, String> LEG = Map.of(TransportLeg.COLLECTION, "Gom về hub nguồn",
        TransportLeg.LINEHAUL, "Trung chuyển giữa hub", TransportLeg.DISTRIBUTION, "Chuyển về chi nhánh đích");
    public static final Map<TripItemStatus, String> ITEM = Map.of(TripItemStatus.MANIFESTED, "Trong manifest, chưa nhận",
        TripItemStatus.RECEIVED, "Đã thực nhận", TripItemStatus.MISSING, "Thiếu kiện", TripItemStatus.DAMAGED, "Cần kiểm tra hư hỏng", TripItemStatus.REJECTED, "Từ chối nhận");
    public static final Map<TripDiscrepancyKind, String> ISSUE = Map.of(TripDiscrepancyKind.MISSING, "Thiếu kiện", TripDiscrepancyKind.DAMAGED,
        "Hư hỏng/cần kiểm tra", TripDiscrepancyKind.REJECTED, "Từ chối nhận", TripDiscrepancyKind.UNEXPECTED, "Kiện ngoài manifest");
    public static final Map<TripEventType, String> EVENT = Map.of(TripEventType.CREATED, "Tạo chuyến", TripEventType.ITEMS_ADDED, "Thêm kiện",
        TripEventType.SEALED, "Niêm phong", TripEventType.DEPARTED, "Xuất chuyến", TripEventType.ARRIVED, "Chuyến đã đến",
        TripEventType.ITEM_RECEIVED, "Kiểm nhận kiện", TripEventType.DISCREPANCY_RECORDED, "Ghi nhận sai lệch",
        TripEventType.DISCREPANCY_RESOLVED, "Xử lý sai lệch ngoài manifest", TripEventType.CLOSED, "Đóng chuyến");
}
