package com.ute.uteexpress.dto.admin;

import com.ute.uteexpress.dto.operations.OperationsLabels;

public final class ReportLabels {
    public String text(Object value){
        if(value==null)return "Tất cả";
        if(value instanceof ReportViews.Dataset d)return d.getLabel();
        return switch(value.toString()){
            case "TODAY"->"Hôm nay";case "LAST_7_DAYS"->"7 ngày";case "LAST_30_DAYS"->"30 ngày";case "THIS_MONTH"->"Tháng hiện tại";case "CUSTOM"->"Chọn khoảng ngày";
            case "DAY"->"Theo ngày";case "WEEK"->"Theo tuần (thứ Hai)";case "MONTH"->"Theo tháng";
            case "ALL"->"Tất cả nguồn";case "ADMIN"->"Audit quản trị";case "SHIPMENT"->"Lịch sử vận đơn";case "TRANSPORT"->"Lịch sử chuyến";case "TICKET"->"Lịch sử hỗ trợ";
            case "SUCCESS"->"Thành công có bằng chứng";case "FAILED"->"Thất bại";case "REQUESTED"->"Đã yêu cầu hoàn";case "READY_FOR_TRANSPORT"->"Sẵn sàng vận chuyển hoàn";case "AT_ORIGIN"->"Đã về nguồn";case "COMPLETED"->"Hoàn tất";
            default->OperationsLabels.chartLabel(value.toString());
        };
    }
}
