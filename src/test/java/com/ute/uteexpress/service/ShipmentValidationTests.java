package com.ute.uteexpress.service;

import com.ute.uteexpress.dto.shipment.ShipmentCreateForm;
import jakarta.validation.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.BeanWrapperImpl;

import static com.ute.uteexpress.shipment.ShipmentFixtures.form;
import static org.junit.jupiter.api.Assertions.*;

class ShipmentValidationTests {
    private static ValidatorFactory factory;
    private static Validator validator;
    @BeforeAll static void setup() { factory = Validation.buildDefaultValidatorFactory(); validator = factory.getValidator(); }
    @AfterAll static void close() { factory.close(); }

    @Test void validFormAndOptionalDimensionsAreAccepted() {
        ShipmentCreateForm form = form(); assertTrue(validator.validate(form).isEmpty());
        form.setLengthCm(new BigDecimal("0.01")); form.setNote("a".repeat(500));
        form.setDeclaredValue(new BigDecimal("9999999999999999.99"));
        assertTrue(validator.validate(form).isEmpty());
        form.setSenderName("  Nguyễn Văn A  "); assertEquals("Nguyễn Văn A", form.getSenderName());
    }

    @ParameterizedTest
    @CsvSource({
        "senderName,101", "senderProvince,101", "senderDistrict,101", "senderWard,101", "senderAddress,256",
        "receiverName,101", "receiverProvince,101", "receiverDistrict,101", "receiverWard,101", "receiverAddress,256",
        "packageDescription,256", "note,501"
    })
    void excessiveTextLengthsAreRejected(String field, int length) {
        ShipmentCreateForm form = form(); new BeanWrapperImpl(form).setPropertyValue(field, "a".repeat(length));
        assertTrue(validator.validate(form).stream().anyMatch(error -> field.equals(error.getPropertyPath().toString())));
    }

    @ParameterizedTest
    @CsvSource({
        "weightKg,0", "weightKg,-1", "weightKg,1.001", "weightKg,100000000",
        "lengthCm,0", "widthCm,-1", "heightCm,0.001", "lengthCm,100000000",
        "codAmount,-0.01", "codAmount,0.001", "declaredValue,-1", "declaredValue,10000000000000000"
    })
    void invalidNumbersAreRejected(String field, String value) {
        ShipmentCreateForm form = form(); new BeanWrapperImpl(form).setPropertyValue(field, new BigDecimal(value));
        assertTrue(validator.validate(form).stream().anyMatch(error -> field.equals(error.getPropertyPath().toString())));
    }

    @ParameterizedTest
    @CsvSource({"senderPhone,123", "receiverPhone,abc1234567", "senderPhone,1234567890123456", "receiverPhone,090-123-4567"})
    void invalidPhonesAreRejected(String field, String value) {
        ShipmentCreateForm form = form(); new BeanWrapperImpl(form).setPropertyValue(field, value);
        assertFalse(validator.validate(form).isEmpty());
    }

    @Test void requiredValuesCannotBeBlankNullOrInvalidIds() {
        ShipmentCreateForm form = form(); form.setSenderName("   "); form.setReceiverAddress(null);
        form.setWeightKg(null); form.setOriginBranchId(-1L); form.setDestinationBranchId(null);
        form.setServiceId(0L); form.setPaymentPayer(null); form.setCodAmount(null);
        var fields = validator.validate(form).stream().map(error -> error.getPropertyPath().toString()).toList();
        assertTrue(fields.containsAll(java.util.List.of("senderName", "receiverAddress", "weightKg",
            "originBranchId", "destinationBranchId", "serviceId", "paymentPayer", "codAmount")));
    }
}
