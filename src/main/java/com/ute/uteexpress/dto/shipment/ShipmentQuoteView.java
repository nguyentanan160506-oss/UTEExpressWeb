package com.ute.uteexpress.dto.shipment;

import com.ute.uteexpress.enums.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Private, owner-scoped review. Never used by Guest Tracking. */
public record ShipmentQuoteView(String token, ShipmentMethod shipmentMethod,
        ShipmentDetailView.Contact sender, ShipmentDetailView.Contact receiver,
        String originBranch, String destinationBranch, String serviceName, String packageDescription,
        BigDecimal weightKg, BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm,
        BigDecimal codAmount, BigDecimal declaredValue, PaymentPayer paymentPayer, String note,
        BigDecimal shippingFee, BigDecimal pickupFee, BigDecimal insuranceFee, BigDecimal totalFee,
        LocalDateTime expiresAt, boolean confirmable, boolean consumed, com.ute.uteexpress.dto.pricing.PriceBreakdown breakdown) {
    public ShipmentQuoteView(String token, ShipmentMethod shipmentMethod,
            ShipmentDetailView.Contact sender, ShipmentDetailView.Contact receiver,
            String originBranch, String destinationBranch, String serviceName, String packageDescription,
            BigDecimal weightKg, BigDecimal lengthCm, BigDecimal widthCm, BigDecimal heightCm,
            BigDecimal codAmount, BigDecimal declaredValue, PaymentPayer paymentPayer, String note,
            BigDecimal shippingFee, BigDecimal pickupFee, BigDecimal insuranceFee, BigDecimal totalFee,
            LocalDateTime expiresAt, boolean confirmable, boolean consumed) {
        this(token, shipmentMethod, sender, receiver, originBranch, destinationBranch, serviceName, packageDescription,
            weightKg, lengthCm, widthCm, heightCm, codAmount, declaredValue, paymentPayer, note,
            shippingFee, pickupFee, insuranceFee, totalFee, expiresAt, confirmable, consumed, null);
    }
}
