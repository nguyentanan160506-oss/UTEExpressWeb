package com.ute.uteexpress.dto.delivery;

import com.ute.uteexpress.enums.*;
import java.util.Map;

public final class DeliveryLabels {
    private DeliveryLabels() { }
    public static final Map<DeliveryDispatchStatus, String> DISPATCH = Map.ofEntries(
        Map.entry(DeliveryDispatchStatus.PREPARED, "Chi nhánh đã chuẩn bị, chờ Shipper thực nhận"), Map.entry(DeliveryDispatchStatus.ACCEPTED, "Shipper đã nhận, chờ bắt đầu"),
        Map.entry(DeliveryDispatchStatus.STARTED, "Đang thực hiện lần giao"), Map.entry(DeliveryDispatchStatus.FAILED, "Giao thất bại, Shipper còn giữ kiện"),
        Map.entry(DeliveryDispatchStatus.RECOVERY_REQUESTED, "Chờ chi nhánh kiểm nhận lại"), Map.entry(DeliveryDispatchStatus.BRANCH_RETURNED, "Chi nhánh đã thực nhận lại"),
        Map.entry(DeliveryDispatchStatus.RECALL_REQUIRED, "Đã quyết định hoàn, cần thu hồi về chi nhánh"), Map.entry(DeliveryDispatchStatus.COMPLETED, "Đã giao thành công"),
        Map.entry(DeliveryDispatchStatus.CANCELLED, "Đã hủy bàn giao chưa nhận"));
    public static final Map<DeliveryFailureCode, String> FAILURE = Map.of(DeliveryFailureCode.UNREACHABLE, "Không liên hệ được", DeliveryFailureCode.ABSENT, "Người nhận vắng mặt",
        DeliveryFailureCode.REFUSED, "Người nhận từ chối", DeliveryFailureCode.INVALID_ADDRESS, "Địa chỉ sai/thiếu", DeliveryFailureCode.OPERATIONAL, "Điều kiện vận hành", DeliveryFailureCode.DAMAGED, "Kiện có dấu hiệu hư hỏng");
    public static final Map<ReturnStage, String> RETURN = Map.of(ReturnStage.REQUESTED, "Chờ thu hồi từ Shipper", ReturnStage.READY_FOR_TRANSPORT, "Đã nhận tại điểm, chờ chuyến hoàn",
        ReturnStage.IN_TRANSIT, "Đang vận chuyển hoàn", ReturnStage.AT_ORIGIN, "Nguồn đã nhận, chờ trả người gửi", ReturnStage.COMPLETED, "Đã trả người gửi/đại diện");
}
