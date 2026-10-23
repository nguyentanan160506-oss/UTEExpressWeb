package com.ute.uteexpress.dto.pricing;

import com.ute.uteexpress.dto.shipment.ShipmentCreateForm;
import com.ute.uteexpress.enums.ShipmentMethod;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/** No contact name, telephone, street address or declared/COD value is collected. */
public class GuestQuoteForm {
    @NotNull(message = "Vui lòng nhập/chọn thông tin này.") private ShipmentMethod shipmentMethod;
    public ShipmentMethod getShipmentMethod() { return shipmentMethod; }
    public void setShipmentMethod(ShipmentMethod value) { shipmentMethod = value; }
    @NotNull(message = "Vui lòng nhập/chọn thông tin này.") @Positive(message = "Giá trị phải lớn hơn 0.") private Long serviceId;
    public Long getServiceId() { return serviceId; }
    public void setServiceId(Long value) { serviceId = value; }
    @Positive(message = "Giá trị phải lớn hơn 0.") private Long originBranchId;
    public Long getOriginBranchId() { return originBranchId; }
    public void setOriginBranchId(Long value) { originBranchId = value; }
    @NotNull(message = "Vui lòng nhập/chọn thông tin này.") @Positive(message = "Giá trị phải lớn hơn 0.") private Long destinationBranchId;
    public Long getDestinationBranchId() { return destinationBranchId; }
    public void setDestinationBranchId(Long value) { destinationBranchId = value; }
    @NotBlank(message = "Không được để trống.") @Size(max = 100, message = "Tối đa {max} ký tự.") private String senderProvince;
    public String getSenderProvince() { return senderProvince; }
    public void setSenderProvince(String value) { senderProvince = value; }
    @NotBlank(message = "Không được để trống.") @Size(max = 100, message = "Tối đa {max} ký tự.") private String senderDistrict;
    public String getSenderDistrict() { return senderDistrict; }
    public void setSenderDistrict(String value) { senderDistrict = value; }
    @NotBlank(message = "Không được để trống.") @Size(max = 100, message = "Tối đa {max} ký tự.") private String senderWard;
    public String getSenderWard() { return senderWard; }
    public void setSenderWard(String value) { senderWard = value; }
    @NotBlank(message = "Không được để trống.") @Size(max = 100, message = "Tối đa {max} ký tự.") private String receiverProvince;
    public String getReceiverProvince() { return receiverProvince; }
    public void setReceiverProvince(String value) { receiverProvince = value; }
    @NotBlank(message = "Không được để trống.") @Size(max = 100, message = "Tối đa {max} ký tự.") private String receiverDistrict;
    public String getReceiverDistrict() { return receiverDistrict; }
    public void setReceiverDistrict(String value) { receiverDistrict = value; }
    @NotBlank(message = "Không được để trống.") @Size(max = 100, message = "Tối đa {max} ký tự.") private String receiverWard;
    public String getReceiverWard() { return receiverWard; }
    public void setReceiverWard(String value) { receiverWard = value; }
    @NotNull(message = "Vui lòng nhập/chọn thông tin này.") @DecimalMin(value = "0.01", message = "Giá trị tối thiểu {value}.") @Digits(integer = 8, fraction = 2, message = "Tối đa {integer} số nguyên và 2 số lẻ.") private BigDecimal weightKg;
    public BigDecimal getWeightKg() { return weightKg; }
    public void setWeightKg(BigDecimal value) { weightKg = value; }
    @DecimalMin(value = "0.01", message = "Giá trị tối thiểu {value}.") @Digits(integer = 8, fraction = 2, message = "Tối đa {integer} số nguyên và 2 số lẻ.") private BigDecimal lengthCm;
    public BigDecimal getLengthCm() { return lengthCm; }
    public void setLengthCm(BigDecimal value) { lengthCm = value; }
    @DecimalMin(value = "0.01", message = "Giá trị tối thiểu {value}.") @Digits(integer = 8, fraction = 2, message = "Tối đa {integer} số nguyên và 2 số lẻ.") private BigDecimal widthCm;
    public BigDecimal getWidthCm() { return widthCm; }
    public void setWidthCm(BigDecimal value) { widthCm = value; }
    @DecimalMin(value = "0.01", message = "Giá trị tối thiểu {value}.") @Digits(integer = 8, fraction = 2, message = "Tối đa {integer} số nguyên và 2 số lẻ.") private BigDecimal heightCm;
    public BigDecimal getHeightCm() { return heightCm; }
    public void setHeightCm(BigDecimal value) { heightCm = value; }
    public ShipmentCreateForm toShipmentInput() {
        var form = new ShipmentCreateForm();
        form.setShipmentMethod(shipmentMethod);
        form.setServiceId(serviceId);
        form.setOriginBranchId(originBranchId);
        form.setDestinationBranchId(destinationBranchId);
        form.setSenderProvince(senderProvince);
        form.setSenderDistrict(senderDistrict);
        form.setSenderWard(senderWard);
        form.setReceiverProvince(receiverProvince);
        form.setReceiverDistrict(receiverDistrict);
        form.setReceiverWard(receiverWard);
        form.setWeightKg(weightKg);
        form.setLengthCm(lengthCm);
        form.setWidthCm(widthCm);
        form.setHeightCm(heightCm);
        return form;
    }
}
