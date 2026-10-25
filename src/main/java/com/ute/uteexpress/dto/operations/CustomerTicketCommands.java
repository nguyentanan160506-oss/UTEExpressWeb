package com.ute.uteexpress.dto.operations;

import com.ute.uteexpress.enums.TicketCategory;
import jakarta.validation.constraints.*;

/** Constructor-bound allowlist: ownership, branch, priority, source and actor are server decisions. */
public final class CustomerTicketCommands {
    private CustomerTicketCommands() { }
    public record Create(@NotNull(message="Vui lòng chọn vận đơn của bạn.") @Positive(message="Vận đơn không hợp lệ.") Long shipmentId,
            @NotNull(message="Vui lòng chọn loại vấn đề.") TicketCategory category,
            @NotBlank(message="Vui lòng nhập tiêu đề.") @Size(max=200,message="Tiêu đề tối đa 200 ký tự.") String subject,
            @NotBlank(message="Vui lòng mô tả vấn đề cần hỗ trợ.") @Size(max=2000,message="Mô tả tối đa 2.000 ký tự.") String content,
            @NotBlank(message="Vui lòng tải lại form.") @Pattern(regexp="[a-f0-9]{32}",message="Mã yêu cầu không hợp lệ. Vui lòng tải lại form.") String token) { }
    public record Message(@NotNull @PositiveOrZero Long version,
            @NotBlank @Size(max=2000) String body,
            @NotBlank @Pattern(regexp="[a-f0-9]{32}") String token) { }
}
