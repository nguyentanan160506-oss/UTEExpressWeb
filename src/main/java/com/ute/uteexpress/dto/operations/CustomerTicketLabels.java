package com.ute.uteexpress.dto.operations;

import com.ute.uteexpress.enums.*;

public final class CustomerTicketLabels {
    public String status(TicketStatus value) {
        return switch(value) {
            case OPEN -> "Đã tiếp nhận";
            case ASSIGNED,PROCESSING,WAITING_INTERNAL -> "Đang xử lý";
            case WAITING_CUSTOMER -> "Chờ bạn phản hồi";
            case RESOLVED -> "Đã giải quyết";
            case CLOSED -> "Đã đóng";
            case REOPENED -> "Đã mở lại";
        };
    }
    public String category(TicketCategory value){return new OperationsLabels().text(value);}
    public String filter(CustomerTicketViews.Filter value){return switch(value){case ALL->"Tất cả";case WORKING->"Đang xử lý";case WAITING_CUSTOMER->"Chờ phản hồi";case RESOLVED->"Đã giải quyết";case CLOSED->"Đã đóng";};}
}
