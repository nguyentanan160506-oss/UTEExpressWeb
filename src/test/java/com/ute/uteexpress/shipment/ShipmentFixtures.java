package com.ute.uteexpress.shipment;

import com.ute.uteexpress.dto.shipment.*;
import com.ute.uteexpress.entity.*;
import com.ute.uteexpress.enums.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.PageImpl;

/** Synthetic records for isolated tests only; never seed or connect to SQL Server. */
public final class ShipmentFixtures {
    private ShipmentFixtures() { }
    public static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 15, 12, 0);
    public static ShipmentCreateForm form() {
        ShipmentCreateForm form = new ShipmentCreateForm();
        form.setSenderName("Người gửi thử nghiệm"); form.setSenderPhone("0901234567");
        form.setSenderProvince("Hồ Chí Minh"); form.setSenderDistrict("Thủ Đức");
        form.setSenderWard("Linh Chiểu"); form.setSenderAddress("Địa chỉ gửi thử nghiệm");
        form.setReceiverName("Người nhận thử nghiệm"); form.setReceiverPhone("+84901234568");
        form.setReceiverProvince("Hồ Chí Minh"); form.setReceiverDistrict("Thủ Đức");
        form.setReceiverWard("Linh Trung"); form.setReceiverAddress("Địa chỉ nhận thử nghiệm");
        form.setOriginBranchId(11L); form.setDestinationBranchId(12L); form.setServiceId(21L);
        form.setPackageDescription("Sách"); form.setWeightKg(new BigDecimal("1.01"));
        return form;
    }
    public static User owner() {
        User user = new User(); user.setId(1L); user.setEmail("shipment-fixture@example.test");
        user.setStatus(UserStatus.ACTIVE); return user;
    }
    public static Branch branch(Long id) {
        Branch branch = new Branch(); branch.setId(id); branch.setName("Chi nhánh thử nghiệm " + id);
        branch.setProvince("Hồ Chí Minh"); branch.setStatus(BranchStatus.ACTIVE); return branch;
    }
    public static ShippingService catalog() {
        ShippingService service = new ShippingService(); service.setId(21L); service.setName("Dịch vụ thử nghiệm");
        service.setActive(true); return service;
    }
    public static ShippingRateRule rate() {
        ShippingRateRule rate = new ShippingRateRule(); rate.setService(catalog());
        rate.setScopeType(RateScopeType.INTRA_PROVINCE); rate.setBaseWeightKg(new BigDecimal("1.00"));
        rate.setBasePrice(new BigDecimal("20000.00")); rate.setExtraWeightStepKg(new BigDecimal("0.50"));
        rate.setExtraPricePerStep(new BigDecimal("5000.00")); rate.setEffectiveFrom(NOW.minusDays(1));
        return rate;
    }
    public static Shipment shipment() {
        Shipment shipment = new Shipment(); shipment.setId(31L); shipment.setSender(owner());
        shipment.setTrackingNumber("UTE_TEST_TRACKING"); shipment.setReceiverName("Người nhận thử nghiệm");
        shipment.setSenderName("Người gửi thử nghiệm"); shipment.setSenderPhone("0901234567");
        shipment.setSenderProvince("Hồ Chí Minh"); shipment.setSenderDistrict("Thủ Đức");
        shipment.setSenderWard("Linh Chiểu"); shipment.setSenderAddress("Địa chỉ gửi thử nghiệm");
        shipment.setReceiverPhone("0901234568"); shipment.setReceiverProvince("Hồ Chí Minh");
        shipment.setReceiverDistrict("Thủ Đức"); shipment.setReceiverWard("Linh Trung"); shipment.setReceiverAddress("Địa chỉ nhận thử nghiệm");
        shipment.setOriginBranch(branch(11L)); shipment.setDestinationBranch(branch(12L)); shipment.setService(catalog());
        shipment.setPackageDescription("Sách"); shipment.setWeightKg(new BigDecimal("1.01"));
        shipment.setShippingFee(new BigDecimal("25000.00")); shipment.setTotalFee(shipment.getShippingFee());
        shipment.setCreatedAt(NOW); shipment.setUpdatedAt(NOW); return shipment;
    }
    public static ShipmentOptions options() {
        return new ShipmentOptions(List.of(new ShipmentOptions.BranchOption(11L, "Chi nhánh thử nghiệm 11", "Hồ Chí Minh")),
            List.of(new ShipmentOptions.ServiceOption(21L, "Dịch vụ thử nghiệm")));
    }
    public static ShipmentDetailView detail() {
        var contact = new ShipmentDetailView.Contact("Khách thử nghiệm", "0901234567", "Hồ Chí Minh",
            "Thủ Đức", "Linh Chiểu", "Địa chỉ thử nghiệm");
        var event = new ShipmentDetailView.EventView("Đã tạo vận đơn", null, null, ShipmentStatus.CREATED, NOW);
        return new ShipmentDetailView(31L, "UTE_TEST_TRACKING", contact, contact, "Chi nhánh gửi", "Chi nhánh nhận", null,
            "Dịch vụ thử nghiệm", "Sách", new BigDecimal("1.01"), null, null, null, BigDecimal.ZERO, BigDecimal.ZERO,
            new BigDecimal("25000.00"), BigDecimal.ZERO, new BigDecimal("25000.00"), PaymentPayer.SENDER,
            ShipmentPaymentStatus.UNPAID, ShipmentStatus.CREATED, null, NOW, NOW, new PageImpl<>(List.of(event)));
    }
}
