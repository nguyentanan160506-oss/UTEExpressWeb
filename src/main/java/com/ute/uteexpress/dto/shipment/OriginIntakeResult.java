package com.ute.uteexpress.dto.shipment;

/** Returned only after the transactional service call; never exposes contacts or internal evidence. */
public record OriginIntakeResult(Long version,String reference,boolean replay) {
    public String notice() {
        return reference==null?"Đã ghi nhận từ chối bàn giao.":
            (replay?"Kiện đã được tiếp nhận trước đó. Biên nhận: ":"Đã tiếp nhận kiện thành công. Biên nhận: ")+reference;
    }
}
