package lk.sliit.electronest.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PostalCodeValidator implements ConstraintValidator<PostalCode, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Optional when omitted; supplied whitespace must not become a valid empty value.
        // Keep the form value intact on failure. The delivery save already trims before storage.
        return value == null || value.isEmpty() || value.trim().matches("[A-Za-z0-9 -]{3,10}");
    }
}
