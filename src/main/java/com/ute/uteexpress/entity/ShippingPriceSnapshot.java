package com.ute.uteexpress.entity;

import com.ute.uteexpress.enums.RateScopeType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Immutable price terms; null embedding denotes a pre-V15 row, never backfilled. */
@Embeddable
public class ShippingPriceSnapshot {
    public static final String CHECK = "(rate_scope IS NULL AND origin_province_code IS NULL AND destination_province_code IS NULL AND base_shipping_fee IS NULL AND extra_weight_fee IS NULL AND priced_base_weight_kg IS NULL AND priced_step_weight_kg IS NULL AND priced_extra_price IS NULL) OR (rate_scope IS NOT NULL AND origin_province_code IS NOT NULL AND destination_province_code IS NOT NULL AND base_shipping_fee IS NOT NULL AND extra_weight_fee IS NOT NULL AND priced_base_weight_kg IS NOT NULL AND priced_step_weight_kg IS NOT NULL AND priced_extra_price IS NOT NULL AND rate_scope IN ('INTRA_PROVINCE', 'SAME_REGION', 'NORTH_CENTRAL', 'CENTRAL_SOUTH', 'NORTH_SOUTH') AND LEN(origin_province_code) = 2 AND LEN(destination_province_code) = 2 AND base_shipping_fee >= 0 AND extra_weight_fee >= 0 AND priced_base_weight_kg > 0 AND priced_step_weight_kg > 0 AND priced_extra_price >= 0 AND shipping_fee = base_shipping_fee + extra_weight_fee)";
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name="rate_scope", length=30, updatable=false) private RateScopeType scope;
    @Column(name="origin_province_code", length=2, updatable=false) private String originProvinceCode;
    @Column(name="destination_province_code", length=2, updatable=false) private String destinationProvinceCode;
    @JdbcTypeCode(SqlTypes.DECIMAL) @Column(name="base_shipping_fee", precision=18, scale=2, updatable=false) private BigDecimal baseFee;
    @JdbcTypeCode(SqlTypes.DECIMAL) @Column(name="extra_weight_fee", precision=18, scale=2, updatable=false) private BigDecimal extraFee;
    @JdbcTypeCode(SqlTypes.DECIMAL) @Column(name="priced_base_weight_kg", precision=10, scale=2, updatable=false) private BigDecimal baseWeightKg;
    @JdbcTypeCode(SqlTypes.DECIMAL) @Column(name="priced_step_weight_kg", precision=10, scale=2, updatable=false) private BigDecimal stepWeightKg;
    @JdbcTypeCode(SqlTypes.DECIMAL) @Column(name="priced_extra_price", precision=18, scale=2, updatable=false) private BigDecimal stepPrice;
    protected ShippingPriceSnapshot() { }
    public ShippingPriceSnapshot(RateScopeType scope, String origin, String destination, BigDecimal baseFee,
            BigDecimal extraFee, BigDecimal baseWeight, BigDecimal stepWeight, BigDecimal stepPrice) {
        this.scope=scope;this.originProvinceCode=origin;this.destinationProvinceCode=destination;
        this.baseFee=baseFee;this.extraFee=extraFee;this.baseWeightKg=baseWeight;this.stepWeightKg=stepWeight;this.stepPrice=stepPrice;
    }
    public ShippingPriceSnapshot copy() { return new ShippingPriceSnapshot(scope,originProvinceCode,destinationProvinceCode,baseFee,extraFee,baseWeightKg,stepWeightKg,stepPrice); }
    public RateScopeType getScope() { return scope; }
    public String getOriginProvinceCode() { return originProvinceCode; }
    public String getDestinationProvinceCode() { return destinationProvinceCode; }
    public BigDecimal getBaseFee() { return baseFee; }
    public BigDecimal getExtraFee() { return extraFee; }
    public BigDecimal getBaseWeightKg() { return baseWeightKg; }
    public BigDecimal getStepWeightKg() { return stepWeightKg; }
    public BigDecimal getStepPrice() { return stepPrice; }
}
