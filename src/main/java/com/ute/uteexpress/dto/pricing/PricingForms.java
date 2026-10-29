package com.ute.uteexpress.dto.pricing;

import com.ute.uteexpress.enums.RateScopeType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;

public final class PricingForms {
    private PricingForms() { }
    public static java.util.List<String> errors(org.springframework.validation.BindingResult errors) {
        return errors.getAllErrors().stream().map(error -> {
            if (error instanceof org.springframework.validation.FieldError f) {
                return label(f.getField()) + ": " + (f.isBindingFailure() ? "Định dạng không hợp lệ." : f.getDefaultMessage());
            }
            return error.getDefaultMessage();
        }).distinct().toList();
    }
    private static String label(String field) {
        return switch (field) {
            case "code" -> "Mã"; case "name" -> "Tên"; case "description" -> "Mô tả"; case "estimatedDeliveryHours" -> "Thời gian giao";
            case "validFrom", "at" -> "Thời điểm bắt đầu/ngừng UTC"; case "validTo" -> "Kết thúc UTC";
            case "expectedVersion", "catalogVersion" -> "Phiên bản dữ liệu"; case "serviceId" -> "Dịch vụ";
            case "originBranchId", "destinationBranchId", "branchId" -> "Chi nhánh"; case "scope" -> "Phạm vi";
            case "basePrice", "extraPricePerStep", "amount" -> "Giá/phụ phí";
            case "weightKg", "baseWeightKg", "extraWeightStepKg", "maxWeightKg" -> "Trọng lượng/bước cân";
            case "lengthCm", "widthCm", "heightCm", "maxLengthCm", "maxWidthCm", "maxHeightCm" -> "Kích thước";
            case "shipmentMethod" -> "Phương thức gửi";
            case "senderProvince", "senderDistrict", "senderWard" -> "Khu vực gửi";
            case "receiverProvince", "receiverDistrict", "receiverWard" -> "Khu vực nhận";
            default -> "Thông tin";
        };
    }
    public static class Request {
        @NotNull(message = "Vui lòng nhập/chọn thông tin này.") @PositiveOrZero(message = "Phiên bản dữ liệu không hợp lệ.") private Long expectedVersion;
        public Long getExpectedVersion() { return expectedVersion; }
        public void setExpectedVersion(Long value) { expectedVersion = value; }
    }
    public static class Catalog {
        @PositiveOrZero(message = "Phiên bản dữ liệu không hợp lệ.") private Long catalogVersion;
        public Long getCatalogVersion() { return catalogVersion; }
        public void setCatalogVersion(Long value) { catalogVersion = value; }
        @Pattern(regexp = "[A-Z0-9][A-Z0-9_-]{0,49}", message = "Mã chỉ dùng A-Z, 0-9, _ và -, tối đa 50 ký tự.") private String code;
        public String getCode() { return code; }
        public void setCode(String value) { code = value; }
        @NotBlank(message = "Không được để trống.") @Size(max = 100, message = "Tối đa {max} ký tự.") private String name;
        public String getName() { return name; }
        public void setName(String value) { name = value; }
        @Size(max = 255, message = "Tối đa {max} ký tự.") private String description;
        public String getDescription() { return description; }
        public void setDescription(String value) { description = value; }
        @NotNull(message = "Vui lòng nhập/chọn thông tin này.") @Min(value = 1, message = "Giá trị phải từ 1 trở lên.") private Integer estimatedDeliveryHours;
        public Integer getEstimatedDeliveryHours() { return estimatedDeliveryHours; }
        public void setEstimatedDeliveryHours(Integer value) { estimatedDeliveryHours = value; }
        @DecimalMin(value = "0.01", message = "Giá trị tối thiểu {value}.") @Digits(integer = 8, fraction = 2, message = "Tối đa {integer} số nguyên và 2 số lẻ.") private BigDecimal maxWeightKg;
        public BigDecimal getMaxWeightKg() { return maxWeightKg; }
        public void setMaxWeightKg(BigDecimal value) { maxWeightKg = value; }
        @DecimalMin(value = "0.01", message = "Giá trị tối thiểu {value}.") @Digits(integer = 8, fraction = 2, message = "Tối đa {integer} số nguyên và 2 số lẻ.") private BigDecimal maxLengthCm;
        public BigDecimal getMaxLengthCm() { return maxLengthCm; }
        public void setMaxLengthCm(BigDecimal value) { maxLengthCm = value; }
        @DecimalMin(value = "0.01", message = "Giá trị tối thiểu {value}.") @Digits(integer = 8, fraction = 2, message = "Tối đa {integer} số nguyên và 2 số lẻ.") private BigDecimal maxWidthCm;
        public BigDecimal getMaxWidthCm() { return maxWidthCm; }
        public void setMaxWidthCm(BigDecimal value) { maxWidthCm = value; }
        @DecimalMin(value = "0.01", message = "Giá trị tối thiểu {value}.") @Digits(integer = 8, fraction = 2, message = "Tối đa {integer} số nguyên và 2 số lẻ.") private BigDecimal maxHeightCm;
        public BigDecimal getMaxHeightCm() { return maxHeightCm; }
        public void setMaxHeightCm(BigDecimal value) { maxHeightCm = value; }
    }
    public static class Draft {
        @NotNull(message = "Vui lòng nhập/chọn thông tin này.") @Positive(message = "Giá trị phải lớn hơn 0.") private Long serviceId;
        public Long getServiceId() { return serviceId; }
        public void setServiceId(Long value) { serviceId = value; }
        @NotBlank(message = "Không được để trống.") @Pattern(regexp = "[A-Z0-9][A-Z0-9_-]{0,49}", message = "Mã chỉ dùng A-Z, 0-9, _ và -, tối đa 50 ký tự.") private String code;
        public String getCode() { return code; }
        public void setCode(String value) { code = value; }
        @NotNull(message = "Vui lòng nhập/chọn thông tin này.") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) private LocalDateTime validFrom;
        public LocalDateTime getValidFrom() { return validFrom; }
        public void setValidFrom(LocalDateTime value) { validFrom = value; }
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) private LocalDateTime validTo;
        public LocalDateTime getValidTo() { return validTo; }
        public void setValidTo(LocalDateTime value) { validTo = value; }
    }
    public static class Period extends Request {
        @NotNull(message = "Vui lòng nhập/chọn thông tin này.") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) private LocalDateTime validFrom;
        public LocalDateTime getValidFrom() { return validFrom; }
        public void setValidFrom(LocalDateTime value) { validFrom = value; }
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) private LocalDateTime validTo;
        public LocalDateTime getValidTo() { return validTo; }
        public void setValidTo(LocalDateTime value) { validTo = value; }
    }
    public static class Shipping extends Request {
        @Positive(message = "Giá trị phải lớn hơn 0.") private Long ruleId;
        public Long getRuleId() { return ruleId; }
        public void setRuleId(Long value) { ruleId = value; }
        @NotNull(message = "Vui lòng nhập/chọn thông tin này.") private RateScopeType scope;
        public RateScopeType getScope() { return scope; }
        public void setScope(RateScopeType value) { scope = value; }
        @NotNull(message = "Vui lòng nhập/chọn thông tin này.") @DecimalMin(value = "0.01", message = "Giá trị tối thiểu {value}.") @Digits(integer = 8, fraction = 2, message = "Tối đa {integer} số nguyên và 2 số lẻ.") private BigDecimal baseWeightKg;
        public BigDecimal getBaseWeightKg() { return baseWeightKg; }
        public void setBaseWeightKg(BigDecimal value) { baseWeightKg = value; }
        @NotNull(message = "Vui lòng nhập/chọn thông tin này.") @DecimalMin(value = "0.00", message = "Giá trị tối thiểu {value}.") @Digits(integer = 16, fraction = 2, message = "Tối đa {integer} số nguyên và 2 số lẻ.") private BigDecimal basePrice;
        public BigDecimal getBasePrice() { return basePrice; }
        public void setBasePrice(BigDecimal value) { basePrice = value; }
        @NotNull(message = "Vui lòng nhập/chọn thông tin này.") @DecimalMin(value = "0.01", message = "Giá trị tối thiểu {value}.") @Digits(integer = 8, fraction = 2, message = "Tối đa {integer} số nguyên và 2 số lẻ.") private BigDecimal extraWeightStepKg;
        public BigDecimal getExtraWeightStepKg() { return extraWeightStepKg; }
        public void setExtraWeightStepKg(BigDecimal value) { extraWeightStepKg = value; }
        @NotNull(message = "Vui lòng nhập/chọn thông tin này.") @DecimalMin(value = "0.00", message = "Giá trị tối thiểu {value}.") @Digits(integer = 16, fraction = 2, message = "Tối đa {integer} số nguyên và 2 số lẻ.") private BigDecimal extraPricePerStep;
        public BigDecimal getExtraPricePerStep() { return extraPricePerStep; }
        public void setExtraPricePerStep(BigDecimal value) { extraPricePerStep = value; }
         private boolean active;
        public boolean isActive() { return active; }
        public void setActive(boolean value) { active = value; }
    }
    public static class Pickup extends Request {
        @Positive(message = "Giá trị phải lớn hơn 0.") private Long ruleId;
        public Long getRuleId() { return ruleId; }
        public void setRuleId(Long value) { ruleId = value; }
        @Positive(message = "Giá trị phải lớn hơn 0.") private Long branchId;
        public Long getBranchId() { return branchId; }
        public void setBranchId(Long value) { branchId = value; }
        @NotNull(message = "Vui lòng nhập/chọn thông tin này.") @DecimalMin(value = "0.01", message = "Giá trị tối thiểu {value}.") @Digits(integer = 16, fraction = 2, message = "Tối đa {integer} số nguyên và 2 số lẻ.") private BigDecimal amount;
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal value) { amount = value; }
         private boolean active;
        public boolean isActive() { return active; }
        public void setActive(boolean value) { active = value; }
    }
    public static class Confirm extends Request {
         private boolean confirmed;
        public boolean isConfirmed() { return confirmed; }
        public void setConfirmed(boolean value) { confirmed = value; }
    }
    public static class Status extends Request {
         private boolean active;
        public boolean isActive() { return active; }
        public void setActive(boolean value) { active = value; }
         private boolean confirmed;
        public boolean isConfirmed() { return confirmed; }
        public void setConfirmed(boolean value) { confirmed = value; }
    }
    public static class RetireDate {
        @NotNull(message = "Vui lòng nhập/chọn thông tin này.") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) private LocalDateTime at;
        public LocalDateTime getAt() { return at; }
        public void setAt(LocalDateTime value) { at = value; }
         private boolean confirmed;
        public boolean isConfirmed() { return confirmed; }
        public void setConfirmed(boolean value) { confirmed = value; }
    }
    public static class Retire extends RetireDate {
        @NotNull(message = "Vui lòng nhập/chọn thông tin này.") @PositiveOrZero(message = "Phiên bản dữ liệu không hợp lệ.") private Long expectedVersion;
        public Long getExpectedVersion() { return expectedVersion; }
        public void setExpectedVersion(Long value) { expectedVersion = value; }
    }
}
