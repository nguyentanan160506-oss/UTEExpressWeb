package com.ute.uteexpress.operations;

import com.ute.uteexpress.dto.pricing.GuestQuoteForm;
import com.ute.uteexpress.dto.shipment.ShipmentCreateForm;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.service.*;
import com.ute.uteexpress.service.notifications.*;
import com.ute.uteexpress.shipment.ShipmentFixtures;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import static com.ute.uteexpress.operations.OperationsJdbcFixture.*;

/** Fixtures are local to each test, including published pricing and explicit branch coverage. */
abstract class CustomerJourneySupport extends OperationsJdbcSupport {
    @Autowired QuoteService quoteService;
    @Autowired UserShipmentService userShipments;
    @Autowired PublicTrackingService trackingService;
    @Autowired ShipmentLifecycleService lifecycle;
    @Autowired TransportTripService transport;
    @Autowired LastMileService lastMile;
    @Autowired ShipmentNotificationProcessor shipmentNotifications;
    @Autowired NotificationService inbox;
    @Autowired NotificationDelivery delivery;
    Long rateId, pickupId, pricingId;

    @BeforeEach void pricingFixture() {
        db(() -> {
            for (Branch b : new Branch[]{f.source, f.destination}) {
                em.find(Branch.class, b.getId()).setParentHub(em.find(Branch.class, f.hub.getId()));
                for (ShipmentMethod method : ShipmentMethod.values()) {
                    var area = new BranchServiceArea(); area.setBranch(b); area.setProvince(b.getProvince());
                    area.setDistrict("Test"); area.setWard("Test"); area.setShipmentMethod(method);
                    area.setSupportsDelivery(true); em.persist(area);
                }
            }
            var version = new PricingVersion(); version.setService(f.catalog); version.setCode("TEST-PUBLISHED");
            version.setStatus(PricingVersionStatus.PUBLISHED); version.setCreatedBy(f.admin); version.setUpdatedBy(f.admin);
            version.setPublishedBy(f.admin); version.setPublishedAt(NOW.minusDays(1));
            version.setValidFrom(NOW.minusDays(1)); em.persist(version);
            for (RateScopeType scope : new RateScopeType[]{RateScopeType.INTRA_PROVINCE, RateScopeType.INTER_PROVINCE}) {
                var rate = ShipmentFixtures.rate(); rate.setService(f.catalog); rate.setScopeType(scope);
                rate.setEffectiveFrom(NOW.minusDays(1)); rate.setPricingVersion(version);
                rate.setBasePrice(new BigDecimal(scope == RateScopeType.INTRA_PROVINCE ? "20000.00" : "40000.00"));
                em.persist(rate); em.flush(); if (scope == RateScopeType.INTER_PROVINCE) rateId = rate.getId();
            }
            var pickup = new PickupFeeRule(); pickup.setBranch(f.source); pickup.setService(f.catalog);
            pickup.setAmount(new BigDecimal("7500.00")); pickup.setEffectiveFrom(NOW.minusDays(1));
            pickup.setPricingVersion(version); em.persist(pickup); em.flush();
            pickupId = pickup.getId(); pricingId = version.getId();
            return null;
        });
        as(f.owner, "ROLE_USER");
    }
    ShipmentCreateForm form() {
        var form = ShipmentFixtures.form(); form.setOriginBranchId(f.source.getId());
        form.setDestinationBranchId(f.destination.getId()); form.setServiceId(f.catalog.getId());
        form.setSenderProvince(f.source.getProvince()); form.setSenderDistrict("Test"); form.setSenderWard("Test");
        form.setReceiverProvince(f.destination.getProvince()); form.setReceiverDistrict("Test"); form.setReceiverWard("Test");
        return form;
    }
    GuestQuoteForm guest(ShipmentCreateForm f) {
        var g = new GuestQuoteForm(); g.setServiceId(f.getServiceId()); g.setShipmentMethod(f.getShipmentMethod());
        g.setOriginBranchId(f.getOriginBranchId()); g.setDestinationBranchId(f.getDestinationBranchId());
        g.setSenderProvince(f.getSenderProvince()); g.setSenderDistrict(f.getSenderDistrict()); g.setSenderWard(f.getSenderWard());
        g.setReceiverProvince(f.getReceiverProvince()); g.setReceiverDistrict(f.getReceiverDistrict()); g.setReceiverWard(f.getReceiverWard());
        g.setWeightKg(f.getWeightKg()); g.setLengthCm(f.getLengthCm()); g.setWidthCm(f.getWidthCm()); g.setHeightCm(f.getHeightCm());
        return g;
    }
    Long create() { return quoteService.confirm(quoteService.quote(form())); }
    Long shipmentVersion(Long id) { return db(() -> em.find(Shipment.class, id).getVersion()); }
    long count(Class<?> type) { return db(() -> em.createQuery("select count(x) from " + type.getSimpleName() + " x", Long.class).getSingleResult()); }
    void constraint(String sql) { db(() -> { em.createNativeQuery(sql).executeUpdate(); return null; }); }
}
