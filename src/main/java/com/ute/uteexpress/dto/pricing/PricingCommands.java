package com.ute.uteexpress.dto.pricing;

import com.ute.uteexpress.enums.RateScopeType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class PricingCommands {
    private PricingCommands() { }
    public record Catalog(@Pattern(regexp = "[A-Z0-9][A-Z0-9_-]{0,49}") String code,
        @NotBlank @Size(max = 100) String name, @Size(max = 255) String description,
        @NotNull @Min(1) Integer estimatedDeliveryHours,
        @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal maxWeightKg,
        @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal maxLengthCm,
        @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal maxWidthCm,
        @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal maxHeightCm) { }
    public record Draft(@NotNull @Positive Long serviceId, @NotBlank @Pattern(regexp = "[A-Z0-9][A-Z0-9_-]{0,49}") String code,
        @NotNull LocalDateTime validFrom, LocalDateTime validTo) { }
    public record Period(@NotNull LocalDateTime validFrom, LocalDateTime validTo) { }
    public record ShippingRule(@Positive Long ruleId, @NotNull RateScopeType scope,
        @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal baseWeightKg,
        @NotNull @DecimalMin("0.00") @Digits(integer = 16, fraction = 2) BigDecimal basePrice,
        @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal extraWeightStepKg,
        @NotNull @DecimalMin("0.00") @Digits(integer = 16, fraction = 2) BigDecimal extraPricePerStep, boolean active) { }
    public record PickupRule(@Positive Long ruleId, @Positive Long branchId,
        @NotNull @DecimalMin("0.01") @Digits(integer = 16, fraction = 2) BigDecimal amount, boolean active) { }
}
