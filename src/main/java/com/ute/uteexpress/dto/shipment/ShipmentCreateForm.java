package com.ute.uteexpress.dto.shipment;

import com.ute.uteexpress.enums.PaymentPayer;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public class ShipmentCreateForm {
    @NotBlank(message = "Vui lòng nhập thông tin này.")
    @Size(max = 100, message = "Tối đa 100 ký tự.")
    private String senderName;

    @NotBlank(message = "Vui lòng nhập thông tin này.")
    @Size(max = 100, message = "Tối đa 100 ký tự.")
    private String senderProvince;

    @NotBlank(message = "Vui lòng nhập thông tin này.")
    @Size(max = 100, message = "Tối đa 100 ký tự.")
    private String senderDistrict;

    @NotBlank(message = "Vui lòng nhập thông tin này.")
    @Size(max = 100, message = "Tối đa 100 ký tự.")
    private String senderWard;

    @NotBlank(message = "Vui lòng nhập thông tin này.")
    @Size(max = 255, message = "Tối đa 255 ký tự.")
    private String senderAddress;

    @NotBlank(message = "Vui lòng nhập số điện thoại.")
    @Pattern(regexp = "\\+?[0-9]{9,15}", message = "Số điện thoại cần có 9–15 chữ số.")
    private String senderPhone;

    @NotBlank(message = "Vui lòng nhập thông tin này.")
    @Size(max = 100, message = "Tối đa 100 ký tự.")
    private String receiverName;

    @NotBlank(message = "Vui lòng nhập thông tin này.")
    @Size(max = 100, message = "Tối đa 100 ký tự.")
    private String receiverProvince;

    @NotBlank(message = "Vui lòng nhập thông tin này.")
    @Size(max = 100, message = "Tối đa 100 ký tự.")
    private String receiverDistrict;

    @NotBlank(message = "Vui lòng nhập thông tin này.")
    @Size(max = 100, message = "Tối đa 100 ký tự.")
    private String receiverWard;

    @NotBlank(message = "Vui lòng nhập thông tin này.")
    @Size(max = 255, message = "Tối đa 255 ký tự.")
    private String receiverAddress;

    @NotBlank(message = "Vui lòng nhập số điện thoại.")
    @Pattern(regexp = "\\+?[0-9]{9,15}", message = "Số điện thoại cần có 9–15 chữ số.")
    private String receiverPhone;

    @NotNull(message = "Vui lòng chọn thông tin này.")
    @Positive(message = "Lựa chọn không hợp lệ.")
    private Long originBranchId;

    @NotNull(message = "Vui lòng chọn thông tin này.")
    @Positive(message = "Lựa chọn không hợp lệ.")
    private Long destinationBranchId;

    @NotNull(message = "Vui lòng chọn thông tin này.")
    @Positive(message = "Lựa chọn không hợp lệ.")
    private Long serviceId;

    @NotBlank(message = "Vui lòng mô tả hàng hóa.")
    @Size(max = 255, message = "Tối đa 255 ký tự.")
    private String packageDescription;

    @NotNull(message = "Vui lòng nhập trọng lượng.")
    @DecimalMin(value = "0.01", message = "Giá trị phải lớn hơn 0.")
    @Digits(integer = 8, fraction = 2, message = "Tối đa 8 chữ số nguyên và 2 chữ số thập phân.")
    private BigDecimal weightKg;

    @DecimalMin(value = "0.01", message = "Giá trị phải lớn hơn 0.")
    @Digits(integer = 8, fraction = 2, message = "Tối đa 8 chữ số nguyên và 2 chữ số thập phân.")
    private BigDecimal lengthCm;

    @DecimalMin(value = "0.01", message = "Giá trị phải lớn hơn 0.")
    @Digits(integer = 8, fraction = 2, message = "Tối đa 8 chữ số nguyên và 2 chữ số thập phân.")
    private BigDecimal widthCm;

    @DecimalMin(value = "0.01", message = "Giá trị phải lớn hơn 0.")
    @Digits(integer = 8, fraction = 2, message = "Tối đa 8 chữ số nguyên và 2 chữ số thập phân.")
    private BigDecimal heightCm;

    @NotNull(message = "Vui lòng nhập số tiền, dùng 0 nếu không có.")
    @DecimalMin(value = "0", message = "Số tiền không được âm.")
    @Digits(integer = 16, fraction = 2, message = "Tối đa 16 chữ số nguyên và 2 chữ số thập phân.")
    private BigDecimal codAmount = new BigDecimal("0.00");

    @NotNull(message = "Vui lòng nhập số tiền, dùng 0 nếu không có.")
    @DecimalMin(value = "0", message = "Số tiền không được âm.")
    @Digits(integer = 16, fraction = 2, message = "Tối đa 16 chữ số nguyên và 2 chữ số thập phân.")
    private BigDecimal declaredValue = new BigDecimal("0.00");

    @NotNull(message = "Vui lòng chọn bên trả phí.")
    private PaymentPayer paymentPayer = PaymentPayer.SENDER;

    @Size(max = 500, message = "Ghi chú tối đa 500 ký tự.")
    private String note;

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName == null ? null : senderName.strip(); }
    public String getSenderProvince() { return senderProvince; }
    public void setSenderProvince(String senderProvince) { this.senderProvince = senderProvince == null ? null : senderProvince.strip(); }
    public String getSenderDistrict() { return senderDistrict; }
    public void setSenderDistrict(String senderDistrict) { this.senderDistrict = senderDistrict == null ? null : senderDistrict.strip(); }
    public String getSenderWard() { return senderWard; }
    public void setSenderWard(String senderWard) { this.senderWard = senderWard == null ? null : senderWard.strip(); }
    public String getSenderAddress() { return senderAddress; }
    public void setSenderAddress(String senderAddress) { this.senderAddress = senderAddress == null ? null : senderAddress.strip(); }
    public String getSenderPhone() { return senderPhone; }
    public void setSenderPhone(String senderPhone) { this.senderPhone = senderPhone == null ? null : senderPhone.strip(); }
    public String getReceiverName() { return receiverName; }
    public void setReceiverName(String receiverName) { this.receiverName = receiverName == null ? null : receiverName.strip(); }
    public String getReceiverProvince() { return receiverProvince; }
    public void setReceiverProvince(String receiverProvince) { this.receiverProvince = receiverProvince == null ? null : receiverProvince.strip(); }
    public String getReceiverDistrict() { return receiverDistrict; }
    public void setReceiverDistrict(String receiverDistrict) { this.receiverDistrict = receiverDistrict == null ? null : receiverDistrict.strip(); }
    public String getReceiverWard() { return receiverWard; }
    public void setReceiverWard(String receiverWard) { this.receiverWard = receiverWard == null ? null : receiverWard.strip(); }
    public String getReceiverAddress() { return receiverAddress; }
    public void setReceiverAddress(String receiverAddress) { this.receiverAddress = receiverAddress == null ? null : receiverAddress.strip(); }
    public String getReceiverPhone() { return receiverPhone; }
    public void setReceiverPhone(String receiverPhone) { this.receiverPhone = receiverPhone == null ? null : receiverPhone.strip(); }
    public Long getOriginBranchId() { return originBranchId; }
    public void setOriginBranchId(Long originBranchId) { this.originBranchId = originBranchId; }
    public Long getDestinationBranchId() { return destinationBranchId; }
    public void setDestinationBranchId(Long destinationBranchId) { this.destinationBranchId = destinationBranchId; }
    public Long getServiceId() { return serviceId; }
    public void setServiceId(Long serviceId) { this.serviceId = serviceId; }
    public String getPackageDescription() { return packageDescription; }
    public void setPackageDescription(String packageDescription) { this.packageDescription = packageDescription == null ? null : packageDescription.strip(); }
    public BigDecimal getWeightKg() { return weightKg; }
    public void setWeightKg(BigDecimal weightKg) { this.weightKg = weightKg; }
    public BigDecimal getLengthCm() { return lengthCm; }
    public void setLengthCm(BigDecimal lengthCm) { this.lengthCm = lengthCm; }
    public BigDecimal getWidthCm() { return widthCm; }
    public void setWidthCm(BigDecimal widthCm) { this.widthCm = widthCm; }
    public BigDecimal getHeightCm() { return heightCm; }
    public void setHeightCm(BigDecimal heightCm) { this.heightCm = heightCm; }
    public BigDecimal getCodAmount() { return codAmount; }
    public void setCodAmount(BigDecimal codAmount) { this.codAmount = codAmount; }
    public BigDecimal getDeclaredValue() { return declaredValue; }
    public void setDeclaredValue(BigDecimal declaredValue) { this.declaredValue = declaredValue; }
    public PaymentPayer getPaymentPayer() { return paymentPayer; }
    public void setPaymentPayer(PaymentPayer paymentPayer) { this.paymentPayer = paymentPayer; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note == null ? null : note.strip(); }
}
