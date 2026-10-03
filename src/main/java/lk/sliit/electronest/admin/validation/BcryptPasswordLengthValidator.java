package lk.sliit.electronest.admin.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

public class BcryptPasswordLengthValidator implements ConstraintValidator<BcryptPasswordLength, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Required and minimum-length validation remain separate registration constraints.
        return value == null || value.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
