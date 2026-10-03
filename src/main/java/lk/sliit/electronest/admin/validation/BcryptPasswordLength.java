package lk.sliit.electronest.admin.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target(FIELD)
@Retention(RUNTIME)
@Constraint(validatedBy = BcryptPasswordLengthValidator.class)
public @interface BcryptPasswordLength {
    String message() default "Password must not exceed 72 bytes.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
