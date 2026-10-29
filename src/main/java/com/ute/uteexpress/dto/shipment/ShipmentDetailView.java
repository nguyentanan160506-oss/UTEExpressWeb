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
        LocalDateTime createdAt, LocalDateTime updatedAt, Page<EventView> events, ShipmentMethod shipmentMethod, BigDecimal pickupFee,
        com.ute.uteexpress.dto.pricing.PriceBreakdown breakdown) {
    public ShipmentDetailView(Long id, String trackingNumber, Contact sender, Contact receiver,
            String originBranch, String destinationBranch, String currentBranch, String serviceName,
            String packageDescription, BigDecimal weightKg, BigDecimal lengthCm, BigDecimal widthCm,
            BigDecimal heightCm, BigDecimal codAmount, BigDecimal declaredValue, BigDecimal shippingFee,
            BigDecimal insuranceFee, BigDecimal totalFee, PaymentPayer paymentPayer,
            ShipmentPaymentStatus paymentStatus, ShipmentStatus status, String note,
            LocalDateTime createdAt, LocalDateTime updatedAt, Page<EventView> events, ShipmentMethod shipmentMethod, BigDecimal pickupFee) {
        this(id, trackingNumber, sender, receiver, originBranch, destinationBranch, currentBranch, serviceName, packageDescription,
            weightKg, lengthCm, widthCm, heightCm, codAmount, declaredValue, shippingFee, insuranceFee, totalFee, paymentPayer,
            paymentStatus, status, note, createdAt, updatedAt, events, shipmentMethod, pickupFee, null);
    }
    public record Contact(String name, String phone, String province, String district, String ward, String address) { }
    public record EventView(String title, String description, String location,
            ShipmentStatus status, LocalDateTime occurredAt) { }
}
