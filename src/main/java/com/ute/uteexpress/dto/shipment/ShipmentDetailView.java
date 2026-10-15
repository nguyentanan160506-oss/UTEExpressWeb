package com.ute.uteexpress.dto.shipment;

import com.ute.uteexpress.enums.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;

public record ShipmentDetailView(Long id, String trackingNumber, Contact sender, Contact receiver,
        String originBranch, String destinationBranch, String currentBranch, String serviceName,
        String packageDescription, BigDecimal weightKg, BigDecimal lengthCm, BigDecimal widthCm,
        BigDecimal heightCm, BigDecimal codAmount, BigDecimal declaredValue, BigDecimal shippingFee,
        BigDecimal insuranceFee, BigDecimal totalFee, PaymentPayer paymentPayer,
        ShipmentPaymentStatus paymentStatus, ShipmentStatus status, String note,
        LocalDateTime createdAt, LocalDateTime updatedAt, Page<EventView> events) {
    public record Contact(String name, String phone, String province, String district, String ward, String address) { }
    public record EventView(String title, String description, String location,
            ShipmentStatus status, LocalDateTime occurredAt) { }
}
