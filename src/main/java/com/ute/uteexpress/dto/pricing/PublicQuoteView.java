package com.ute.uteexpress.dto.pricing;

import com.ute.uteexpress.enums.ShipmentMethod;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Public estimate has no internal rule/version IDs, contacts, policy amounts or persistence token. */
public record PublicQuoteView(String service, ShipmentMethod method, BigDecimal shippingFee, BigDecimal pickupFee,
    BigDecimal insuranceFee, BigDecimal totalFee, String currency, LocalDateTime estimatedAt, LocalDateTime expiresAt) { }
