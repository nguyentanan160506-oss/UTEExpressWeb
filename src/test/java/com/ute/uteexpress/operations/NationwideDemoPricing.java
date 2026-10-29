package com.ute.uteexpress.operations;

import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.RateScopeType;
import jakarta.persistence.EntityManager;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Assumed academic demo tariffs, not market prices. Test source/resources never enter the production JAR. */
final class NationwideDemoPricing {
    private NationwideDemoPricing() { }
    static void seed(EntityManager em, ShippingService service, PricingVersion version) {
        // The enclosing LocalDemoSeed has already verified its freshly allocated jdbc:h2:mem:uteexpress_demo_*.
        try (var stream = NationwideDemoPricing.class.getResourceAsStream("/demo/nationwide-shipping-prices.csv")) {
            if (stream == null) { throw new IllegalStateException("Demo price fixture missing"); }
            var seen = EnumSet.noneOf(RateScopeType.class);
            for (var line : new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).lines().skip(1).toList()) {
                var parts = line.split(","); var scope = RateScopeType.valueOf(parts[0]);
                if (!seen.add(scope) || !RateScopeType.nationwide().contains(scope)) { throw new IllegalStateException("Duplicate or invalid demo price scope"); }
                var rate = new ShippingRateRule(); rate.setService(service); rate.setPricingVersion(version); rate.setScopeType(scope);
                rate.setEffectiveFrom(version.getValidFrom()); rate.setBasePrice(new BigDecimal(parts[1])); rate.setBaseWeightKg(new BigDecimal(parts[2]));
                rate.setExtraWeightStepKg(new BigDecimal(parts[3])); rate.setExtraPricePerStep(new BigDecimal(parts[4])); em.persist(rate);
            }
            if (seen.size() != 5) { throw new IllegalStateException("Five demo price scopes required"); }
            var fee = new PickupFeeRule(); fee.setService(service); fee.setPricingVersion(version);
            fee.setEffectiveFrom(version.getValidFrom()); fee.setAmount(new BigDecimal("10000.00")); em.persist(fee);
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }
}
