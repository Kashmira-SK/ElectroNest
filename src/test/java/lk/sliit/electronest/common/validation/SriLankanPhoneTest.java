package lk.sliit.electronest.common.validation;

import jakarta.validation.Validation;
import lk.sliit.electronest.admin.dto.RegisterForm;
import lk.sliit.electronest.vendor.model.dto.VendorRegistrationRequest;
import lk.sliit.electronest.vendor.model.dto.VendorProfileUpdateRequest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SriLankanPhoneTest {
    @ParameterizedTest
    @ValueSource(strings = {"0771234567", "0112345678", "+94771234567", "+94112345678"})
    void acceptsCompleteNumbers(String phone) {
        check(phone, true);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"077123456", "07712345678", "771234567", "+9477123456",
            "+947712345678", "077ABC4567", "12345", " ", "077 123 4567", "077-1234567",
            "+910771234567", "0771234567\n"})
    void rejectsIncompleteOrMalformedNumbers(String phone) {
        check(phone, false);
    }

    private void check(String phone, boolean valid) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var user = new RegisterForm();
            user.setContactNumber(phone);
            var application = new VendorRegistrationRequest();
            application.setContactPhone(phone);
            var profile = new VendorProfileUpdateRequest();
            profile.setContactPhone(phone);
            assertEquals(valid, validator.validateProperty(user, "contactNumber").isEmpty());
            assertEquals(valid, validator.validateProperty(application, "contactPhone").isEmpty());
            assertEquals(valid, validator.validateProperty(profile, "contactPhone").isEmpty());
        }
    }
}
