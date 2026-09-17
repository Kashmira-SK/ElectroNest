package lk.sliit.electronest.admin.dto;

import lk.sliit.electronest.common.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Bound to templates/auth/register.html via th:object="${registerForm}".
 * Public registration only ever creates CUSTOMER or VENDOR accounts -
 * see AuthController for why ADMIN is excluded from the dropdown.
 */
@Data
public class RegisterForm {

    @NotBlank(message = "Full name is required")
    @Size(max = 100, message = "Full name must be 100 characters or fewer")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    @Size(max = 150, message = "Email must be 150 characters or fewer")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    @NotBlank(message = "Please confirm your password")
    private String confirmPassword;

    @NotBlank(message = "Contact number is required")
    @jakarta.validation.constraints.Pattern(
            regexp = lk.sliit.electronest.common.validation.SriLankanPhone.REGEX,
            message = "Enter a valid contact phone number")
    private String contactNumber;

    private Role role = Role.CUSTOMER;
}
