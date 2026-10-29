package com.ute.uteexpress.service;

import java.math.*;

/** The single base + ceiling(step) calculation used by QuoteService and stored quote verification. */
public final class ShippingChargeCalculator {
    private static final BigDecimal MAX_MONEY = new BigDecimal("9999999999999999.99");
    private ShippingChargeCalculator() { }
    public record Charges(BigDecimal base, BigDecimal extra, BigDecimal total) { }
    public static Charges calculate(BigDecimal weight, BigDecimal baseWeight, BigDecimal basePrice,
            BigDecimal stepWeight, BigDecimal stepPrice) {
        for (var value : new BigDecimal[] {weight, baseWeight, stepWeight}) {
            if (value == null || value.signum() <= 0 || value.scale() > 2 || value.compareTo(new BigDecimal("99999999.99")) > 0) {
                throw new ShipmentCreationException("weightKg", "Trọng lượng và bước cân phải dương, tối đa hai số lẻ và trong giới hạn lưu trữ.");
            }
        }
        BigDecimal base = money(basePrice), step = money(stepPrice);
        BigDecimal steps = weight.subtract(baseWeight).max(BigDecimal.ZERO).divide(stepWeight, 0, RoundingMode.CEILING);
        BigDecimal extra = money(steps.multiply(step));
        return new Charges(base, extra, money(base.add(extra)));
    }
    public static BigDecimal money(BigDecimal value) {
        if (value != null && value.compareTo(MAX_MONEY) > 0) { throw new ShipmentCreationException("weightKg", "Phí vượt giới hạn lưu trữ. Vui lòng liên hệ hỗ trợ."); }
        if (value == null || value.signum() < 0) {
            throw new ShipmentCreationException("serviceId", "Bảng giá không hợp lệ hoặc phí vượt giới hạn lưu trữ.");
        }
        try { return value.setScale(2, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException e) { throw new ShipmentCreationException("serviceId", "Bảng giá chỉ được có tối đa hai số lẻ."); }
    }
}
