package lk.sliit.electronest.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target(FIELD)
@Retention(RUNTIME)
@Constraint(validatedBy = PostalCodeValidator.class)
public @interface PostalCode {
    String message() default "Postal code must be 3 to 10 characters and contain only letters, numbers, spaces, or hyphens.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
