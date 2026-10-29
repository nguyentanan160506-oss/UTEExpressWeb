package com.ute.uteexpress.dto.pricing;

import com.ute.uteexpress.entity.ShippingPriceSnapshot;
import com.ute.uteexpress.enums.RateScopeType;
import java.math.BigDecimal;

/** Safe display projection: no mutable entity, rule ID, or internal pricing-version ID. */
public record PriceBreakdown(RateScopeType scope, BigDecimal baseFee, BigDecimal extraFee) {
    public static PriceBreakdown of(ShippingPriceSnapshot value) {
        return value == null ? null : new PriceBreakdown(value.getScope(), value.getBaseFee(), value.getExtraFee());
    }
}
