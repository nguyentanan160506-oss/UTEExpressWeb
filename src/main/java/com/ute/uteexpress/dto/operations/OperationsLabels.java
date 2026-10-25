package com.ute.uteexpress.dto.operations;

import com.ute.uteexpress.dto.shipment.ShipmentStatusLabels;
import com.ute.uteexpress.enums.*;
import java.util.*;

public final class OperationsLabels {
    private static final Map<String,String> TEXT=new HashMap<>();
    static {
        ShipmentStatusLabels.VALUES.forEach((k,v)->TEXT.put(k.name(),v));
        String[][] values={
            {"OPEN","Mới"},{"ASSIGNED","Đã phân công"},{"PROCESSING","Đang xử lý"},{"WAITING_CUSTOMER","Chờ khách phản hồi"},{"WAITING_INTERNAL","Chờ xử lý nội bộ"},{"RESOLVED","Đã giải quyết"},{"CLOSED","Đã đóng"},{"REOPENED","Đã mở lại"},
            {"LOW","Thấp"},{"MEDIUM","Trung bình"},{"HIGH","Cao"},{"URGENT","Khẩn cấp"},
            {"DELAY","Giao chậm"},{"NOT_RECEIVED","Chưa nhận hàng"},{"DELIVERY_FAILURE","Phản ánh giao thất bại"},{"LOSS_REPORTED","Phản ánh thất lạc (chưa kết luận)"},{"DAMAGE_REPORTED","Phản ánh hư hỏng (chưa kết luận)"},{"WRONG_RECIPIENT","Sai người nhận"},{"ADDRESS","Thông tin giao hàng"},{"SHIPPING_FEE","Phí vận chuyển"},{"RETURN","Hoàn hàng"},{"STAFF_CONDUCT","Thái độ phục vụ"},{"OTHER","Khác"},
            {"UNREACHABLE","Không liên lạc được"},{"ABSENT","Người nhận vắng mặt"},{"REFUSED","Từ chối nhận"},{"INVALID_ADDRESS","Địa chỉ không hợp lệ"},{"OPERATIONAL","Vấn đề vận hành"},{"DAMAGED","Kiện hư hỏng"},
            {"MANAGER_PHONE","Manager tiếp nhận qua điện thoại"},{"MANAGER_EMAIL","Manager tiếp nhận qua email"},{"MANAGER_COUNTER","Manager tiếp nhận tại quầy"},{"CUSTOMER","Khách hàng trực tiếp"},{"INTERNAL","Nội bộ"},{"PUBLIC","Phản hồi công khai cho chủ ticket"},
            {"CUSTOMER_MESSAGE","Khách hàng bổ sung thông tin"},{"REOPEN_REQUESTED","Khách hàng yêu cầu mở lại"},{"REASSIGNED","Phân công lại"},{"PRIORITY_CHANGED","Đổi ưu tiên"},{"CATEGORY_CHANGED","Đổi phân loại"},{"STATUS_CHANGED","Đổi trạng thái"},{"INTERNAL_NOTE","Thêm ghi chú nội bộ"},{"PUBLIC_REPLY","Gửi phản hồi công khai"},
            {"DRAFT","Chờ niêm phong"},{"SEALED","Chờ xuất phát"},{"RECONCILING","Chờ kiểm nhận/đối soát"}};
        for(String[] v:values)TEXT.put(v[0],v[1]);
    }
    public String text(Enum<?> value){if(value==null)return "Chưa xác định";if(value==TicketAction.CREATED)return "Tạo yêu cầu hỗ trợ";return chartLabel(value.name());}
    public String text(String value){return value==null?"Chưa xác định":chartLabel(value);}
    public static String chartLabel(String value){return TEXT.getOrDefault(value,value);}
}
