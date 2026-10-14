package com.ute.uteexpress.service;

import com.ute.uteexpress.dto.VerificationForm;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

class RegistrationValidationTests {
    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll static void setup() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }
    @AfterAll static void close() { factory.close(); }

    @Test void validRegistrationPassesBeanValidation() {
        assertTrue(validator.validate(RegistrationServiceTests.form()).isEmpty());
    }

    @Test void invalidEmailNameUsernameAndPhoneAreRejected() {
        var form = RegistrationServiceTests.form();
        form.setEmail("invalid");
        form.setFullName(" ");
        form.setUsername("x");
        form.setPhoneNumber("phone");
        assertTrue(validator.validate(form).size() >= 4);
    }

    @Test void shortAndMismatchedPasswordsAreRejected() {
        var form = RegistrationServiceTests.form();
        form.setPassword("short");
        form.setConfirmPassword("different");
        assertTrue(validator.validate(form).size() >= 2);
    }

    @Test void unicodePasswordCannotExceedBcryptByteLimit() {
        var form = RegistrationServiceTests.form();
        form.setPassword("ệ".repeat(25));
        form.setConfirmPassword(form.getPassword());
        assertFalse(validator.validate(form).isEmpty());
    }

    @Test void sixDigitOtpIncludesLeadingZerosAndRejectsOtherInput() {
        var form = new VerificationForm();
        form.setEmail("fixture@example.test");
        form.setOtp("000001");
        assertTrue(validator.validate(form).isEmpty());
        form.setOtp("12345");
        assertFalse(validator.validate(form).isEmpty());
        form.setOtp("abc123");
        assertFalse(validator.validate(form).isEmpty());
    }

    @Test void secureGeneratorAlwaysProducesSixDecimalDigits() {
        var generator = new OtpCodeGenerator();
        for (int i = 0; i < 100; i++) {
            assertTrue(generator.generate().matches("[0-9]{6}"));
        }
    }
}
