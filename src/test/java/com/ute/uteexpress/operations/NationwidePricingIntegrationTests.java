package com.ute.uteexpress.operations;

import com.ute.uteexpress.dto.pricing.*;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import com.ute.uteexpress.service.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import static com.ute.uteexpress.operations.OperationsJdbcFixture.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual JPA/repositories, method security and transactions, isolated H2 only. */
@SpringJUnitConfig(CustomerJourneyConfig.class)
class NationwidePricingIntegrationTests extends CustomerJourneySupport {
    @Autowired AdminPricingService adminPricing;
    private static final Map<RateScopeType,String> PRICES=Map.of(RateScopeType.INTRA_PROVINCE,"20000.00",RateScopeType.SAME_REGION,"30000.00",
        RateScopeType.NORTH_CENTRAL,"40000.00",RateScopeType.CENTRAL_SOUTH,"45000.00",RateScopeType.NORTH_SOUTH,"60000.00");
    @BeforeEach void nationwideFixture() {
        db(()->{
            var version=em.find(PricingVersion.class,pricingId);
            for(var scope:RateScopeType.nationwide()) {
                var rows=em.createQuery("select r from ShippingRateRule r where r.pricingVersion.id=:id and r.scopeType=:scope",ShippingRateRule.class).setParameter("id",pricingId).setParameter("scope",scope).getResultList();
                var r=rows.isEmpty()?new ShippingRateRule():rows.getFirst();r.setService(f.catalog);r.setPricingVersion(version);r.setScopeType(scope);
                r.setEffectiveFrom(version.getValidFrom());r.setBaseWeightKg(new BigDecimal("1.00"));r.setExtraWeightStepKg(new BigDecimal("1.00"));
                r.setBasePrice(new BigDecimal(PRICES.get(scope)));r.setExtraPricePerStep(new BigDecimal("5000.00"));if(rows.isEmpty())em.persist(r);
            }
            var p=new PickupFeeRule();p.setService(f.catalog);p.setPricingVersion(version);p.setEffectiveFrom(version.getValidFrom());p.setAmount(new BigDecimal("10000.00"));em.persist(p);
            em.find(PickupFeeRule.class,pickupId).setAmount(new BigDecimal("10000.00"));return null;
        });
    }
    private GuestQuoteForm national(String a,String b,ShipmentMethod method) {
        var g=new GuestQuoteForm();g.setNationwide(true);g.setShipmentMethod(method);g.setServiceId(f.catalog.getId());g.setSenderProvince(a);g.setReceiverProvince(b);g.setWeightKg(new BigDecimal("1.01"));return g;
    }
    @Test void all1156DirectedPairsAndBothMethodsUsePublishedDatabasePricesWithoutBranchesOrWrites() {
        SecurityContextHolder.clearContext();int pairs=0;
        for(var a:ProvinceCatalog.all())for(var b:ProvinceCatalog.all()) {
            var scope=ProvinceCatalog.classify(a.code(),b.code());var base=new BigDecimal(PRICES.get(scope));
            for(var method:ShipmentMethod.values()) {
                var view=quoteService.estimate(national(a.name(),b.name(),method));
                var pickup=new BigDecimal(method==ShipmentMethod.HOME_PICKUP?"10000.00":"0.00");
                assertEquals(base.add(new BigDecimal("5000.00")).add(pickup),view.totalFee(),a.code()+" -> "+b.code()+" "+method);
                assertEquals(scope,view.breakdown().scope());assertEquals(base,view.breakdown().baseFee());
                assertEquals(new BigDecimal("5000.00"),view.breakdown().extraFee());assertEquals(pickup,view.pickupFee());
            }
            pairs++;
        }
        assertEquals(1156,pairs);assertEquals(0,count(ShipmentQuote.class));assertEquals(0,count(Shipment.class));assertEquals(0,count(ShipmentEvent.class));
    }
    @Test void nationalEstimateDoesNotGrantCoverageOrAllowAnUnservedShipment() {
        var estimate=quoteService.estimate(national("Hà Nội","Cà Mau",ShipmentMethod.HOME_PICKUP));assertEquals(new BigDecimal("75000.00"),estimate.totalFee());
        var input=form();input.setSenderProvince("Hà Nội");input.setReceiverProvince("Cà Mau");input.setShipmentMethod(ShipmentMethod.HOME_PICKUP);
        assertThrows(ShipmentCreationException.class,()->quoteService.quote(input));assertEquals(0,count(Shipment.class));assertEquals(0,count(ShipmentQuote.class));
    }
    @Test void specificBranchSurchargeWinsButAmbiguousRulesNeverFallbackToGlobal() {
        db(()->{em.find(PickupFeeRule.class,pickupId).setAmount(new BigDecimal("12000.00"));return null;});
        var input=form();input.setShipmentMethod(ShipmentMethod.HOME_PICKUP);
        assertEquals(new BigDecimal("12000.00"),quoteService.review(quoteService.quote(input)).pickupFee());
        db(()->{var p=new PickupFeeRule();p.setBranch(f.source);p.setService(f.catalog);p.setEffectiveFrom(NOW.minusHours(2));p.setAmount(new BigDecimal("13000.00"));em.persist(p);return null;});
        assertThrows(ShipmentCreationException.class,()->quoteService.quote(input));assertEquals(1,count(ShipmentQuote.class));
    }
    @Test void missingOrDisabledGroupAndMissingGlobalPickupFailClearlyRatherThanUsingLegacyOrZero() {
        db(()->{em.createQuery("update ShippingRateRule r set r.isActive=false where r.scopeType=:scope").setParameter("scope",RateScopeType.NORTH_SOUTH).executeUpdate();return null;});
        assertThrows(ShipmentCreationException.class,()->quoteService.estimate(national("Hà Nội","Cà Mau",ShipmentMethod.BRANCH_DROPOFF)));
        db(()->{em.createQuery("update PickupFeeRule r set r.isActive=false where r.branch is null").executeUpdate();return null;});
        assertThrows(ShipmentCreationException.class,()->quoteService.estimate(national("Đà Nẵng","Hồ Chí Minh",ShipmentMethod.HOME_PICKUP)));
        assertEquals(new BigDecimal("50000.00"),quoteService.estimate(national("Đà Nẵng","Hồ Chí Minh",ShipmentMethod.BRANCH_DROPOFF)).totalFee());
    }
    @ParameterizedTest @EnumSource(ShipmentMethod.class)
    void confirmationRecalculatesStoredTermsAndSnapshotsEveryPriceWithoutFollowingLaterTariffChanges(ShipmentMethod method) {
        var input=form();input.setShipmentMethod(method);String token=quoteService.quote(input);
        var held=quoteService.review(token);Long id=quoteService.confirm(token);
        db(()->{em.find(ShippingRateRule.class,rateId).setBasePrice(new BigDecimal("99000.00"));em.find(PickupFeeRule.class,pickupId).setAmount(new BigDecimal("17000.00"));return null;});
        assertEquals(id,quoteService.confirm(token));assertEquals(held.totalFee(),userShipments.detail(id,0).totalFee());
        db(()->{var s=em.find(Shipment.class,id);assertEquals("79",s.getPriceSnapshot().getOriginProvinceCode());assertEquals("48",s.getPriceSnapshot().getDestinationProvinceCode());
            assertEquals(RateScopeType.CENTRAL_SOUTH,s.getPriceSnapshot().getScope());assertEquals(new BigDecimal("45000.00"),s.getPriceSnapshot().getBaseFee());
            assertEquals(new BigDecimal("5000.00"),s.getPriceSnapshot().getExtraFee());return null;});
        assertEquals(1,count(Shipment.class));assertEquals(1,count(ShipmentEvent.class));
    }
    @Test void heldQuoteUsesItsOwnTermsEvenWhenPriceChangesBeforeConfirmation() {
        String token=quoteService.quote(form());var held=quoteService.review(token);
        db(()->{em.find(ShippingRateRule.class,rateId).setBasePrice(new BigDecimal("50000.00"));return null;});
        Long id=quoteService.confirm(token);assertEquals(held.totalFee(),userShipments.detail(id,0).totalFee());
        assertEquals(new BigDecimal("55000.00"),quoteService.review(quoteService.quote(form())).totalFee());
    }
    @Test void legacyShipmentAddressesPaymentStatusAndPriceRemainUntouchedWithoutNewSnapshot() {
        Long id=db(()->{var s=f.shipment(f.source,f.destination,ShipmentStatus.CREATED,NOW.minusDays(7));s.setSenderProvince("Quảng Nam");s.setReceiverProvince("Bình Dương");
            s.setShipmentMethod(null);s.setPaymentStatus(ShipmentPaymentStatus.PAID);s.setShippingFee(new BigDecimal("12345.67"));s.setTotalFee(s.getShippingFee());em.flush();return s.getId();});
        var view=userShipments.detail(id,0);assertEquals("Quảng Nam",view.sender().province());assertEquals("Bình Dương",view.receiver().province());
        assertEquals(new BigDecimal("12345.67"),view.totalFee());assertEquals(ShipmentPaymentStatus.PAID,view.paymentStatus());
        db(()->{assertNull(em.find(Shipment.class,id).getPriceSnapshot());return null;});
    }
    @Test void adminPublishesFiveGroupsAndGlobalPickupThenNewQuotesChangeWhileExistingShipmentDoesNot() {
        Long id=create();var old=userShipments.detail(id,0).totalFee();as(f.admin,"ROLE_ADMIN");
        var from=NOW.plusMinutes(1);var version=adminPricing.createDraft(new PricingCommands.Draft(f.catalog.getId(),"NEW-NATIONWIDE",from,null));
        for(var scope:RateScopeType.nationwide()) {
            Long expected=db(()->em.find(PricingVersion.class,version).getVersion());
            adminPricing.shippingRule(version,expected,new PricingCommands.ShippingRule(null,scope,new BigDecimal("1.00"),new BigDecimal("50000.00"),new BigDecimal("1.00"),new BigDecimal("5000.00"),true));
        }
        adminPricing.pickupRule(version,db(()->em.find(PricingVersion.class,version).getVersion()),new PricingCommands.PickupRule(null,null,new BigDecimal("11000.00"),true));
        adminPricing.retireVersion(pricingId,db(()->em.find(PricingVersion.class,pricingId).getVersion()),from,true);
        assertTrue(adminPricing.check(version).isEmpty());adminPricing.publish(version,db(()->em.find(PricingVersion.class,version).getVersion()),true);
        clock.set(from.plusSeconds(1));as(f.owner,"ROLE_USER");
        assertEquals(old,userShipments.detail(id,0).totalFee());assertEquals(new BigDecimal("55000.00"),quoteService.review(quoteService.quote(form())).totalFee());
        assertEquals(new BigDecimal("66000.00"),quoteService.estimate(national("Hà Nội","Cà Mau",ShipmentMethod.HOME_PICKUP)).totalFee());
    }
    @ParameterizedTest @ValueSource(strings={"USER","SHIPPER","MANAGER"})
    void nonAdminCannotChangeTariffs(String role) {
        var user=role.equals("USER")?f.owner:role.equals("SHIPPER")?f.shipper:f.manager;as(user,role);
        assertThrows(AccessDeniedException.class,()->adminPricing.createDraft(new PricingCommands.Draft(f.catalog.getId(),"FORGED",NOW.plusHours(1),null)));
        assertEquals(1,count(PricingVersion.class));
    }
    @Test void newCatalogAndDraftUseManagedSaveResultForNonNullIdsAndTransactionalAudit() {
        as(f.admin,"ROLE_ADMIN");
        Long service=adminPricing.createService(new PricingCommands.Catalog("NATIONAL-NEW","Dịch vụ test",null,48,new BigDecimal("30.00"),null,null,null));
        assertNotNull(service);Long version=adminPricing.createDraft(new PricingCommands.Draft(service,"FIRST",NOW.plusMinutes(1),null));assertNotNull(version);
        db(()->{assertNotNull(em.find(ShippingService.class,service));assertNotNull(em.find(PricingVersion.class,version));
            assertEquals(1L,em.createQuery("select count(a) from AuditLog a where a.action='SERVICE_CREATED' and a.resourceId=:id",Long.class).setParameter("id",service.toString()).getSingleResult());
            assertEquals(1L,em.createQuery("select count(a) from AuditLog a where a.action='PRICE_DRAFT_CREATED' and a.resourceId=:id",Long.class).setParameter("id",version.toString()).getSingleResult());return null;});
    }
    @Test void simultaneousConfirmationCreatesOnlyOneShipmentWithOneImmutablePriceSnapshot() throws Exception {
        String token=quoteService.quote(form());var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        try {
            Callable<Long> task=()->{as(f.owner,"ROLE_USER");try{start.await();return quoteService.confirm(token);}finally{SecurityContextHolder.clearContext();}};
            var a=pool.submit(task);var b=pool.submit(task);start.countDown();
            assertEquals(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS));assertEquals(1,count(Shipment.class));assertEquals(1,count(ShipmentEvent.class));
        } finally { pool.shutdownNow(); }
    }
    @Test void formulaMismatchRollsBackConfirmationWithoutConsumingQuote() {
        String token=quoteService.quote(form());
        db(()->{em.createNativeQuery("UPDATE dbo.shipment_quotes SET priced_extra_price=5001 WHERE token=:token").setParameter("token",token).executeUpdate();return null;});
        assertThrows(ShipmentCreationException.class,()->quoteService.confirm(token));assertEquals(0,count(Shipment.class));assertEquals(0,count(ShipmentEvent.class));
        assertFalse(quoteService.review(token).consumed());
    }
    @Test void coverageEstimateCannotBypassRequiredFieldsUsingNewNationalModeFlag() {
        var input=national("Đà Nẵng","Hồ Chí Minh",ShipmentMethod.HOME_PICKUP);input.setNationwide(false);
        assertThrows(jakarta.validation.ConstraintViolationException.class,()->quoteService.estimate(input));
        assertEquals(0,count(ShipmentQuote.class));
    }
    @Test void duplicateNationwideDefaultInDraftIsRejectedUnderTheExistingServiceGate() {
        as(f.admin,"ROLE_ADMIN");Long id=adminPricing.createDraft(new PricingCommands.Draft(f.catalog.getId(),"DEFAULT-UNIQUE",NOW.plusHours(1),null));
        var command=new PricingCommands.PickupRule(null,null,new BigDecimal("11000.00"),true);
        adminPricing.pickupRule(id,db(()->em.find(PricingVersion.class,id).getVersion()),command);
        assertThrows(AdminRuleException.class,()->adminPricing.pickupRule(id,db(()->em.find(PricingVersion.class,id).getVersion()),command));
        db(()->{assertEquals(1L,em.createQuery("select count(r) from PickupFeeRule r where r.pricingVersion.id=:id and r.branch is null",Long.class).setParameter("id",id).getSingleResult());return null;});
    }
    @Test void nationalPricingStillHonorsConfiguredWeightAndDimensionLimits() {
        db(()->{var s=em.find(ShippingService.class,f.catalog.getId());s.setMaxWeightKg(new BigDecimal("1.00"));return null;});
        assertThrows(ShipmentCreationException.class,()->quoteService.estimate(national("Hà Nội","Cà Mau",ShipmentMethod.BRANCH_DROPOFF)));
        db(()->{var s=em.find(ShippingService.class,f.catalog.getId());s.setMaxWeightKg(null);s.setMaxLengthCm(new BigDecimal("30.00"));return null;});
        var input=national("Hà Nội","Cà Mau",ShipmentMethod.HOME_PICKUP);
        assertThrows(ShipmentCreationException.class,()->quoteService.estimate(input));input.setLengthCm(new BigDecimal("30.01"));
        assertThrows(ShipmentCreationException.class,()->quoteService.estimate(input));input.setLengthCm(new BigDecimal("30.00"));
        assertNotNull(quoteService.estimate(input));
    }
}
